package io.github.lamspace.meili.serialize.jackson3;

import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import java.lang.reflect.Field;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.AnnotatedParameter;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * {@link MeiliDocumentSerializer} implementation on Jackson 3 (tools.jackson).
 *
 * <p>Behavior mirror of core's {@code Jackson2DocumentSerializer}: the caller-supplied base
 * mapper is rebuilt through {@code copy()}-equivalent semantics — the base instance itself
 * is never mutated — and on that derived builder the same fixed defaults are layered: the
 * meili naming bridge (below), ISO dates instead of numeric timestamps, and unknown
 * document properties ignored on read. Jackson 3 bundles {@code java.time} support
 * natively, so no JSR-310 module registration is needed; ISO output is additionally
 * pinned explicitly rather than relying on the generation's default.
 *
 * <p>Naming bridge: identical rule to the Jackson 2 implementation — a property whose
 * backing field carries {@code @MeiliField(name=...)} is bound to that document name on
 * both serialization and deserialization (explicit name wins over {@code @JsonProperty},
 * which wins over the Java name), so the settings projection and the stored JSON cannot
 * disagree. Note that Jackson 3 keeps {@code com.fasterxml.jackson.annotation} as the
 * annotation artifact; the bridge reads the same annotation classes both generations see.
 *
 * <p>Instances are thread-safe (Jackson mappers are) and immutable after construction.
 */
public class Jackson3DocumentSerializer implements MeiliDocumentSerializer {

    /** The frozen derived mapper configured for document work. */
    private final ObjectMapper mapper;

    /**
     * Builds a serializer over a configuration-equivalent copy of {@code base}.
     *
     * @param base mapper supplying user configuration; not retained, not mutated
     */
    public Jackson3DocumentSerializer(ObjectMapper base) {
        this.mapper = base.rebuild()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .annotationIntrospector(new MeiliNameIntrospector())
                .build();
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
         * @param config the mapper configuration in effect
         * @param a      the annotated element under consideration
         * @return the effective binding name, possibly {@code null}
         */
        @Override
        public PropertyName findNameForSerialization(MapperConfig<?> config, Annotated a) {
            PropertyName meili = meiliName(a);
            return meili != null ? meili : super.findNameForSerialization(config, a);
        }

        /**
         * Deserialization counterpart of the same bridge, keeping read and write sides
         * symmetric for renamed properties.
         *
         * @param config the mapper configuration in effect
         * @param a      the annotated creator/field/member under consideration
         * @return the effective binding name, possibly {@code null}
         */
        @Override
        public PropertyName findNameForDeserialization(MapperConfig<?> config, Annotated a) {
            PropertyName meili = meiliName(a);
            return meili != null ? meili : super.findNameForDeserialization(config, a);
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
                return af.getAnnotated();
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
