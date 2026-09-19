package io.github.lamspace.meili.core.serialize;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.mapping.MeiliField;
import java.lang.reflect.Field;

/**
 * Default {@link MeiliDocumentSerializer} on Jackson 2.
 *
 * <p>Construction semantics: the caller-supplied base mapper is {@code copy()}-ied —
 * the base instance itself is never mutated — and on that copy the following fixed
 * defaults are layered: the meili naming bridge (below), JSR-310 support, ISO dates
 * instead of numeric timestamps, unknown document properties ignored on read, and the
 * annotation introspector replaced with the bridge subclass. A container-provided mapper
 * therefore keeps its modules and visibility/naming configuration (that is how a global
 * SNAKE_CASE policy survives), while a fully custom {@code AnnotationIntrospector} on the
 * base is overridden by the bridge.
 *
 * <p>Naming bridge: a property whose backing field carries {@code @MeiliField(name=...)}
 * is bound to that document name on both serialization and deserialization; the rule is
 * the same priority chain as {@code MeiliNames.docName} (explicit name wins over
 * {@code @JsonProperty}, which wins over the Java name), so the settings projection and
 * the stored JSON cannot disagree. The lookup normalizes getters and record accessors
 * back to the declared field, which is the only place the annotation is guaranteed to
 * appear (its target is FIELD, propagated onto record components).
 *
 * <p>Instances are thread-safe (Jackson mappers are) and immutable after construction.
 */
public class Jackson2DocumentSerializer implements MeiliDocumentSerializer {

    /** The frozen copy configured for document work. */
    private final ObjectMapper mapper;

    /**
     * Builds a serializer over a copy of {@code base}.
     *
     * @param base mapper supplying user configuration; not retained, not mutated
     */
    public Jackson2DocumentSerializer(ObjectMapper base) {
        this.mapper = base.copy()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setAnnotationIntrospector(new MeiliNameIntrospector());
    }

    @Override
    public String write(Object document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (Exception e) {
            throw new MeiliOrmException("序列化文档失败: "
                    + (document == null ? "null" : document.getClass().getName()), e);
        }
    }

    @Override
    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new MeiliOrmException("反序列化文档失败: " + type.getName(), e);
        }
    }

    /**
     * Introspector that injects {@code @MeiliField.name} as the effective property name
     * ahead of all default Jackson naming, and otherwise defers to the stock
     * {@link JacksonAnnotationIntrospector} behavior.
     */
    private static final class MeiliNameIntrospector extends JacksonAnnotationIntrospector {

        /** Fixed serialization version identifier. */
        private static final long serialVersionUID = 1L;

        /** Instantiated once per serializer copy; all bridge logic is static. */
        MeiliNameIntrospector() {
        }

        /**
         * Serialization-side bridge for {@code @MeiliField.name}.
         *
         * @param a the annotated element under consideration
         * @return the effective binding name, possibly {@code null}
         */
        @Override
        public PropertyName findNameForSerialization(Annotated a) {
            PropertyName meili = meiliName(a);
            return meili != null ? meili : super.findNameForSerialization(a);
        }

        /**
         * Deserialization counterpart of the same bridge, keeping read and write sides
         * symmetric for renamed properties.
         *
         * @param a the annotated creator/field/member under consideration
         * @return the effective binding name, possibly {@code null}
         */
        @Override
        public PropertyName findNameForDeserialization(Annotated a) {
            PropertyName meili = meiliName(a);
            return meili != null ? meili : super.findNameForDeserialization(a);
        }

        /**
         * Resolves the {@code @MeiliField} name from the property's backing field.
         *
         * @param a any annotated element encountered by Jackson's property collection
         * @return an explicit {@link PropertyName}, or {@code null} to defer
         */
        private static PropertyName meiliName(Annotated a) {
            Field field = backingField(a);
            if (field == null) {
                return null;
            }
            MeiliField mf = field.getAnnotation(MeiliField.class);
            return mf != null && !mf.name().isEmpty() ? new PropertyName(mf.name()) : null;
        }

        /**
         * Maps a Jackson introspection target back to the declared Java field: fields pass
         * through; methods normalize {@code getX}/{@code isX}/record-accessor names; creator
         * parameters use Jackson's implicit parameter name.
         *
         * @param a the introspected element
         * @return the backing field, or {@code null} when none can be identified
         */
        private static Field backingField(Annotated a) {
            if (a instanceof AnnotatedField af) {
                return (Field) af.getAnnotated();
            }
            Class<?> declaring;
            String name;
            if (a instanceof AnnotatedMethod am) {
                declaring = am.getDeclaringClass();
                name = beanName(am.getName());
            } else if (a instanceof AnnotatedParameter ap) {
                declaring = ap.getOwner().getDeclaringClass();
                name = ap.getName();
            } else {
                return null;
            }
            return findField(declaring, name);
        }

        /**
         * Strips bean accessor prefixes to recover the Java property name.
         *
         * @param methodName the accessor method name
         * @return the presumed property name (unchanged for record accessors)
         */
        private static String beanName(String methodName) {
            if (methodName.startsWith("get") && methodName.length() > 3
                    && Character.isUpperCase(methodName.charAt(3))) {
                return decapitalize(methodName.substring(3));
            }
            if (methodName.startsWith("is") && methodName.length() > 2
                    && Character.isUpperCase(methodName.charAt(2))) {
                return decapitalize(methodName.substring(2));
            }
            return methodName;
        }

        /**
         * JavaBeans-style decapitalization that preserves all-caps leading runs.
         *
         * @param name the stripped property name
         * @return the field-side name to look up
         */
        private static String decapitalize(String name) {
            if (name.isEmpty()) {
                return name;
            }
            if (name.length() > 1 && Character.isUpperCase(name.charAt(1))
                    && Character.isUpperCase(name.charAt(0))) {
                return name;
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        /**
         * Finds a declared field by name walking the class hierarchy.
         *
         * @param type the starting class
         * @param name the field name
         * @return the field, or {@code null}
         */
        private static Field findField(Class<?> type, String name) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // continue up the hierarchy
                }
            }
            return null;
        }
    }
}
