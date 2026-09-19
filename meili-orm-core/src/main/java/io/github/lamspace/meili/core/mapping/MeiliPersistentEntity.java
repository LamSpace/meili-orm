package io.github.lamspace.meili.core.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable metamodel of one document entity: index name, primary-key property and
 * accessor, flattened dotted paths with their settings roles, and the passthrough
 * settings declarations. Produced exclusively through {@link #of(Class)}, typically via
 * the caching {@link MeiliMappingContext}.
 *
 * <p>Thread model: parsing runs once per requested class (atomic under
 * {@link MeiliMappingContext#getEntity(Class)}); the resulting instance exposes no
 * mutation path, so it is freely shareable across threads.
 *
 * <p>Validation contract — {@link #of(Class)} fails fast with {@link MeiliMappingException}
 * in this order, and every message names the offending entity and member:
 * <ol>
 *   <li>{@code @MeiliDocument} present with a non-blank {@code indexName};</li>
 *   <li>exactly one {@code @MeiliId} whose type is String/Long/long/Integer/int;</li>
 *   <li>projection-name resolution per {@link MeiliNames#docName} (a {@code @MeiliField.name}
 *       conflicting with {@code @JsonProperty} is fatal);</li>
 *   <li>role flags only on leaf properties — never on a field that itself expands;</li>
 *   <li>distinct {@code searchableOrder} values among explicitly ordered searchable fields.</li>
 * </ol>
 *
 * <p>Flattening: non-simple, non-collection aggregate fields expand into
 * {@code parent.child} paths up to 3 segments; cycles and depth overflow stop at a leaf,
 * and collection/array members are opaque leaves by design (no recursion into generics).
 * Value conversion is deliberately out of scope — converters serialize entities through
 * Jackson, this class only answers "which document paths exist and what roles they play".
 */
public final class MeiliPersistentEntity {

    /** Maximum dotted-path segment count produced by nested flattening. */
    private static final int MAX_FLATTEN_DEPTH = 3;

    /** Annotated entity class. */
    private final Class<?> type;
    /** Target index uid, guaranteed non-blank. */
    private final String indexName;
    /** The single primary-key property. */
    private final MeiliPersistentProperty idProperty;
    /** Primary-key read accessor (getter/record component), or {@code null} when the field is used. */
    private final Method idReadMethod;
    /** Primary-key backing field, fallback accessor and type metadata. */
    private final Field idField;
    /** All leaf properties including the id, sorted by dotted path. */
    private final List<MeiliPersistentProperty> properties;
    /** {@code @MeiliSetting} resource paths in declaration order. */
    private final List<String> settingPaths;

    /**
     * Frozen view constructor; only {@link #of(Class)} calls it.
     *
     * @param type         entity class
     * @param indexName    validated index uid
     * @param idProperty   primary-key property
     * @param idReadMethod primary-key accessor method or {@code null}
     * @param idField      primary-key backing field (accessible flag already applied when needed)
     * @param properties   sorted leaf property list
     * @param settingPaths passthrough resource paths in declaration order
     */
    private MeiliPersistentEntity(Class<?> type, String indexName, MeiliPersistentProperty idProperty,
                                  Method idReadMethod, Field idField,
                                  List<MeiliPersistentProperty> properties, List<String> settingPaths) {
        this.type = type;
        this.indexName = indexName;
        this.idProperty = idProperty;
        this.idReadMethod = idReadMethod;
        this.idField = idField;
        this.properties = Collections.unmodifiableList(properties);
        this.settingPaths = Collections.unmodifiableList(settingPaths);
    }

    /**
     * Parses and validates the mapping declarations of one entity class.
     *
     * @param type the annotated entity class (POJO or record)
     * @return the immutable metamodel
     * @throws MeiliMappingException when any rule of the class-level validation contract is
     *         violated
     */
    public static MeiliPersistentEntity of(Class<?> type) {
        MeiliDocument doc = type.getAnnotation(MeiliDocument.class);
        if (doc == null) {
            throw new MeiliMappingException("实体 " + type.getName() + " 缺少 @MeiliDocument 声明");
        }
        if (doc.indexName().isBlank()) {
            throw new MeiliMappingException("实体 " + type.getName() + " 的 indexName 不能为空");
        }

        List<Field> fields = collectFields(type);
        List<Field> idFields = new ArrayList<>();
        for (Field f : fields) {
            if (f.isAnnotationPresent(MeiliId.class)) {
                idFields.add(f);
            }
        }
        if (idFields.isEmpty()) {
            throw new MeiliMappingException("实体 " + type.getName() + " 缺少 @MeiliId 主键声明");
        }
        if (idFields.size() > 1) {
            throw new MeiliMappingException("实体 " + type.getName() + " 声明了 " + idFields.size()
                    + " 个 @MeiliId，主键有且仅有一个");
        }
        Field idField = idFields.get(0);
        Class<?> idType = idField.getType();
        if (idType != String.class && idType != Long.class && idType != long.class
                && idType != Integer.class && idType != int.class) {
            throw new MeiliMappingException("实体 " + type.getName() + " 的主键 " + idField.getName()
                    + " 类型非法: " + idType.getName() + "，MeiliSearch 主键仅允许 String 或整型");
        }

        List<MeiliPersistentProperty> properties = new ArrayList<>();
        Deque<Class<?>> pathTypes = new ArrayDeque<>();
        pathTypes.push(type);
        flatten(type, fields, "", 1, pathTypes, properties);
        validateSearchableOrders(type, properties);
        properties.sort(Comparator.comparing(MeiliPersistentProperty::getJsonPath));

        MeiliPersistentProperty idProperty = properties.stream()
                .filter(MeiliPersistentProperty::isId).findFirst().orElseThrow();
        Method idReadMethod = resolveAccessor(type, idField.getName());
        if (idReadMethod == null) {
            idField.setAccessible(true);
        }

        List<String> settingPaths = new ArrayList<>();
        for (MeiliSetting setting : type.getAnnotationsByType(MeiliSetting.class)) {
            settingPaths.add(setting.settingPath());
        }
        return new MeiliPersistentEntity(type, doc.indexName(), idProperty, idReadMethod,
                idField, properties, settingPaths);
    }

    /**
     * Collects candidate mapping fields of a class: the declared fields of the whole
     * superclass chain minus synthetic, static and {@code @JsonIgnore} members.
     *
     * @param owner the class whose hierarchy is scanned
     * @return candidate fields, subclass fields first
     */
    private static List<Field> collectFields(Class<?> owner) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                JsonIgnore ignored = f.getAnnotation(JsonIgnore.class);
                if (ignored != null && ignored.value()) {
                    continue;
                }
                out.add(f);
            }
        }
        return out;
    }

    /**
     * Expands one field level into leaf properties, recursing through aggregate fields
     * while the cycle stack and depth budget allow.
     *
     * @param owner      root entity class, used only in failure messages
     * @param fields     candidate fields of the current level
     * @param prefix     dotted path prefix of the current level ({@code ""} at the root)
     * @param depth      path segment count of the fields at this level
     * @param pathTypes  aggregate types on the current expansion path (cycle guard)
     * @param out        accumulator of produced leaf properties
     */
    private static void flatten(Class<?> owner, List<Field> fields, String prefix, int depth,
                                Deque<Class<?>> pathTypes, List<MeiliPersistentProperty> out) {
        for (Field f : fields) {
            String path = prefix + MeiliNames.docName(f, f.getName());
            boolean isId = f.isAnnotationPresent(MeiliId.class);
            MeiliField mf = f.getAnnotation(MeiliField.class);
            boolean rolesPresent = mf != null
                    && (mf.searchable() || mf.filterable() || mf.sortable() || mf.displayed());
            Class<?> fieldType = f.getType();
            boolean aggregate = !isId
                    && !MeiliNames.isSimpleType(fieldType)
                    && !pathTypes.contains(fieldType)
                    && depth < MAX_FLATTEN_DEPTH;
            if (aggregate) {
                if (rolesPresent) {
                    throw new MeiliMappingException("实体 " + owner.getName() + " 的角色注解声明在将展开为"
                            + "点路径的容器字段 " + path + " 上，请标注到其叶子属性");
                }
                pathTypes.push(fieldType);
                flatten(owner, collectFields(fieldType), path + ".", depth + 1, pathTypes, out);
                pathTypes.pop();
                continue;
            }
            out.add(new MeiliPersistentProperty(path, isId,
                    mf != null && mf.searchable(), mf != null ? mf.searchableOrder() : -1,
                    mf != null && mf.filterable(), mf != null && mf.sortable(),
                    mf != null && mf.displayed()));
        }
    }

    /**
     * Rejects duplicated explicit searchable orders — ordering would otherwise depend on
     * field traversal order instead of the declaration.
     *
     * @param owner      root entity class for the failure message
     * @param properties parsed leaf properties
     */
    private static void validateSearchableOrders(Class<?> owner,
                                                 List<MeiliPersistentProperty> properties) {
        Set<Integer> seen = new HashSet<>();
        for (MeiliPersistentProperty p : properties) {
            if (p.isSearchable() && p.getSearchableOrder() >= 0 && !seen.add(p.getSearchableOrder())) {
                throw new MeiliMappingException("实体 " + owner.getName() + " 的 searchableOrder="
                        + p.getSearchableOrder() + " 重复（字段 " + p.getJsonPath() + "）");
            }
        }
    }

    /**
     * Finds a public no-arg primary-key accessor: {@code getX}, {@code isX}, or the
     * record component accessor {@code x} — in that order.
     *
     * @param owner    entity class
     * @param javaName declared name of the primary-key field
     * @return the accessor method, or {@code null} when only field access remains
     */
    private static Method resolveAccessor(Class<?> owner, String javaName) {
        String cap = Character.toUpperCase(javaName.charAt(0)) + javaName.substring(1);
        for (String candidate : new String[]{"get" + cap, "is" + cap, javaName}) {
            try {
                Method m = owner.getMethod(candidate);
                if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers())) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    /**
     * Returns the annotated entity class.
     *
     * @return the entity type this metamodel describes
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Returns the target index uid.
     *
     * @return validated non-blank index name from {@code @MeiliDocument}
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Returns the primary-key property.
     *
     * @return the single property with {@link MeiliPersistentProperty#isId()}
     */
    public MeiliPersistentProperty getIdProperty() {
        return idProperty;
    }

    /**
     * Reads the primary-key value of one entity instance via the cached accessor
     * (getter → record component → setAccessible field, in that resolution order).
     *
     * @param entity an instance of {@link #getType()}; {@code null} is rejected
     * @return the primary-key value (String/Long/Integer), possibly {@code null} when the
     *         entity carries no key yet
     * @throws IllegalArgumentException when the argument is not an instance of the entity type
     * @throws MeiliMappingException    when the cached accessor cannot be invoked
     */
    public Object idValue(Object entity) {
        if (!type.isInstance(entity)) {
            throw new IllegalArgumentException("实体实例类型不符: 期望 " + type.getName()
                    + "，实际 " + (entity == null ? "null" : entity.getClass().getName()));
        }
        try {
            return idReadMethod != null ? idReadMethod.invoke(entity) : idField.get(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new MeiliMappingException("读取主键失败: " + type.getName() + "." + idField.getName(), e);
        }
    }

    /**
     * Returns every leaf property including the primary key, sorted by dotted path —
     * a stable order across parses.
     *
     * @return unmodifiable property list
     */
    public List<MeiliPersistentProperty> getProperties() {
        return properties;
    }

    /**
     * Returns the declared passthrough settings resources in annotation order.
     *
     * @return unmodifiable list of {@code settingPath} values (possibly empty)
     */
    public List<String> getSettingPaths() {
        return settingPaths;
    }

    @Override
    public String toString() {
        return "MeiliPersistentEntity[" + indexName + " (" + type.getName() + ")]";
    }
}
