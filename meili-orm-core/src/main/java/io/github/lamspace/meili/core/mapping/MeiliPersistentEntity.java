/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lamspace.meili.core.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
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
 * accessor, flattened dotted paths with their settings roles, root-level audit fields,
 * and the passthrough settings declarations. Produced exclusively through
 * {@link #of(Class)}, typically via the caching {@link MeiliMappingContext}.
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
 *   <li>distinct {@code searchableOrder} values among explicitly ordered searchable fields;</li>
 *   <li>{@code @CreatedDate}/{@code @LastModifiedDate} fields whose type is within the allowed
 *       set Instant/OffsetDateTime/ZonedDateTime/LocalDateTime/long/Long (checked per field
 *       during flattening, at any nesting level).</li>
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
    /** Root-level {@code @CreatedDate} fields, declaration order, already made accessible. */
    private final List<Field> createdDateFields;
    /** Root-level {@code @LastModifiedDate} fields, declaration order, already made accessible. */
    private final List<Field> lastModifiedDateFields;
    /** {@code @MeiliSetting} resource paths in declaration order. */
    private final List<String> settingPaths;

    /**
     * Frozen view constructor; only {@link #of(Class)} calls it.
     *
     * @param type                   entity class
     * @param indexName              validated index uid
     * @param idProperty             primary-key property
     * @param idReadMethod           primary-key accessor method or {@code null}
     * @param idField                primary-key backing field (accessible flag already applied when needed)
     * @param properties             sorted leaf property list
     * @param createdDateFields      root-level created-date fields (accessible flag already applied)
     * @param lastModifiedDateFields root-level last-modified-date fields (accessible flag already applied)
     * @param settingPaths           passthrough resource paths in declaration order
     */
    private MeiliPersistentEntity(Class<?> type, String indexName, MeiliPersistentProperty idProperty,
                                  Method idReadMethod, Field idField,
                                  List<MeiliPersistentProperty> properties,
                                  List<Field> createdDateFields, List<Field> lastModifiedDateFields,
                                  List<String> settingPaths) {
        this.type = type;
        this.indexName = indexName;
        this.idProperty = idProperty;
        this.idReadMethod = idReadMethod;
        this.idField = idField;
        this.properties = Collections.unmodifiableList(properties);
        this.createdDateFields = Collections.unmodifiableList(createdDateFields);
        this.lastModifiedDateFields = Collections.unmodifiableList(lastModifiedDateFields);
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
            throw new MeiliMappingException("entity " + type.getName() + " lacks a @MeiliDocument declaration");
        }
        if (doc.indexName().isBlank()) {
            throw new MeiliMappingException("entity " + type.getName() + " must declare a non-blank indexName");
        }

        List<Field> fields = collectFields(type);
        List<Field> idFields = new ArrayList<>();
        for (Field f : fields) {
            if (f.isAnnotationPresent(MeiliId.class)) {
                idFields.add(f);
            }
        }
        if (idFields.isEmpty()) {
            throw new MeiliMappingException("entity " + type.getName()
                    + " lacks a @MeiliId primary key declaration");
        }
        if (idFields.size() > 1) {
            throw new MeiliMappingException("entity " + type.getName() + " declares " + idFields.size()
                    + " @MeiliId fields; exactly one primary key is required");
        }
        Field idField = idFields.get(0);
        Class<?> idType = idField.getType();
        if (idType != String.class && idType != Long.class && idType != long.class
                && idType != Integer.class && idType != int.class) {
            throw new MeiliMappingException("entity " + type.getName() + " has illegal primary key "
                    + idField.getName() + ": " + idType.getName()
                    + ", MeiliSearch primary keys allow only String or integer types");
        }

        List<MeiliPersistentProperty> properties = new ArrayList<>();
        List<Field> createdDateFields = new ArrayList<>();
        List<Field> lastModifiedDateFields = new ArrayList<>();
        Deque<Class<?>> pathTypes = new ArrayDeque<>();
        pathTypes.push(type);
        flatten(type, fields, "", 1, pathTypes, properties, createdDateFields, lastModifiedDateFields);
        for (Field f : createdDateFields) {
            f.setAccessible(true);
        }
        for (Field f : lastModifiedDateFields) {
            f.setAccessible(true);
        }
        validateSearchableOrders(type, properties);
        properties.sort(Comparator.comparing(MeiliPersistentProperty::getJsonPath));

        MeiliPersistentProperty idProperty = properties.stream()
                .filter(MeiliPersistentProperty::isId).findFirst().orElseThrow();
        Method idReadMethod = resolveAccessor(type, idField.getName());
        if (idReadMethod != null) {
            // public accessor on a package-private host class stays inaccessible otherwise
            idReadMethod.setAccessible(true);
        } else {
            idField.setAccessible(true);
        }

        List<String> settingPaths = new ArrayList<>();
        for (MeiliSetting setting : type.getAnnotationsByType(MeiliSetting.class)) {
            settingPaths.add(setting.settingPath());
        }
        return new MeiliPersistentEntity(type, doc.indexName(), idProperty, idReadMethod,
                idField, properties, createdDateFields, lastModifiedDateFields, settingPaths);
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
     * while the cycle stack and depth budget allow. Audit declarations are validated at
     * every level but only root-level audit fields are collected into the out-lists —
     * nested audit semantics are deliberately out of scope.
     *
     * @param owner       root entity class, used only in failure messages
     * @param fields      candidate fields of the current level
     * @param prefix      dotted path prefix of the current level ({@code ""} at the root)
     * @param depth       path segment count of the fields at this level
     * @param pathTypes   aggregate types on the current expansion path (cycle guard)
     * @param out         accumulator of produced leaf properties
     * @param createdOut  accumulator of root-level {@code @CreatedDate} fields
     * @param modifiedOut accumulator of root-level {@code @LastModifiedDate} fields
     */
    private static void flatten(Class<?> owner, List<Field> fields, String prefix, int depth,
                                Deque<Class<?>> pathTypes, List<MeiliPersistentProperty> out,
                                List<Field> createdOut, List<Field> modifiedOut) {
        for (Field f : fields) {
            String path = prefix + MeiliNames.docName(f, f.getName());
            boolean isId = f.isAnnotationPresent(MeiliId.class);
            boolean created = f.isAnnotationPresent(CreatedDate.class);
            boolean modified = f.isAnnotationPresent(LastModifiedDate.class);
            Class<?> fieldType = f.getType();
            if (created || modified) {
                validateAuditType(owner, f, fieldType);
            }
            MeiliField mf = f.getAnnotation(MeiliField.class);
            boolean rolesPresent = mf != null
                    && (mf.searchable() || mf.filterable() || mf.sortable() || mf.displayed());
            boolean aggregate = !isId
                    && !MeiliNames.isSimpleType(fieldType)
                    && !pathTypes.contains(fieldType)
                    && depth < MAX_FLATTEN_DEPTH;
            if (aggregate) {
                if (rolesPresent) {
                    throw new MeiliMappingException("entity " + owner.getName()
                            + " declares a role annotation on container field " + path
                            + " that expands to dotted paths; annotate its leaf properties instead");
                }
                pathTypes.push(fieldType);
                flatten(owner, collectFields(fieldType), path + ".", depth + 1, pathTypes, out,
                        createdOut, modifiedOut);
                pathTypes.pop();
                continue;
            }
            if (created && prefix.isEmpty()) {
                createdOut.add(f);
            }
            if (modified && prefix.isEmpty()) {
                modifiedOut.add(f);
            }
            out.add(new MeiliPersistentProperty(path, isId, created, modified,
                    mf != null && mf.searchable(), mf != null ? mf.searchableOrder() : -1,
                    mf != null && mf.filterable(), mf != null && mf.sortable(),
                    mf != null && mf.displayed()));
        }
    }

    /**
     * Rejects audit declarations on types outside the write-path conversion set — the
     * filler can only produce these six shapes from one instant.
     *
     * @param owner     root entity class for the failure message
     * @param f         the field carrying an audit annotation
     * @param fieldType declared type of that field
     */
    private static void validateAuditType(Class<?> owner, Field f, Class<?> fieldType) {
        if (fieldType != Instant.class && fieldType != OffsetDateTime.class
                && fieldType != ZonedDateTime.class && fieldType != LocalDateTime.class
                && fieldType != long.class && fieldType != Long.class) {
            throw new MeiliMappingException("entity " + owner.getName() + " audit field " + f.getName()
                    + " has illegal type " + fieldType.getName()
                    + ": @CreatedDate/@LastModifiedDate allow only Instant/OffsetDateTime/"
                    + "ZonedDateTime/LocalDateTime/long/Long");
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
                throw new MeiliMappingException("entity " + owner.getName() + " has duplicate searchableOrder="
                        + p.getSearchableOrder() + " (field " + p.getJsonPath() + ")");
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
            throw new IllegalArgumentException("entity instance type mismatch: expected " + type.getName()
                    + ", actual " + (entity == null ? "null" : entity.getClass().getName()));
        }
        try {
            return idReadMethod != null ? idReadMethod.invoke(entity) : idField.get(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new MeiliMappingException("failed to read primary key: "
                    + type.getName() + "." + idField.getName(), e);
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
     * Returns the root-level {@code @CreatedDate} fields the write path fills, in
     * declaration order. Nested declarations are excluded by contract (audit semantics
     * reach top-level fields only). Fields are already {@code setAccessible}-tuned.
     *
     * @return unmodifiable list of declared fields (possibly empty)
     */
    public List<Field> getCreatedDateFields() {
        return createdDateFields;
    }

    /**
     * Returns the root-level {@code @LastModifiedDate} fields the write path overwrites,
     * in declaration order; same nesting and accessibility rules as
     * {@link #getCreatedDateFields()}.
     *
     * @return unmodifiable list of declared fields (possibly empty)
     */
    public List<Field> getLastModifiedDateFields() {
        return lastModifiedDateFields;
    }

    /**
     * Fast-path predicate for the write side: {@code false} means the save path must
     * behave exactly as if the audit capability did not exist (same instance returned,
     * no reflection touched).
     *
     * @return {@code true} if any root-level audit field was declared
     */
    public boolean hasAuditFields() {
        return !createdDateFields.isEmpty() || !lastModifiedDateFields.isEmpty();
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
