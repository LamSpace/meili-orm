package io.github.lamspace.meili.core.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.lang.reflect.AccessibleObject;
import java.util.Collection;
import java.util.Map;

/**
 * Single source of truth for the "projection name = serialized name" rule: both the
 * metamodel (this package) and the document serializers resolve a member's document
 * field name through {@link #docName(AccessibleObject, String)}, so settings arrays and
 * the JSON actually stored can never disagree.
 *
 * <p>Also hosts the simple/aggregate type predicate that bounds nested flattening.
 * Stateless; thread-safe.
 */
public final class MeiliNames {

    /**
     * Never called — utility holder.
     */
    private MeiliNames() {
    }

    /**
     * Resolves the document field name of a member: {@code @MeiliField.name} wins, then
     * an explicit {@code @JsonProperty} value, then the Java name.
     *
     * @param member   the field (or equivalent accessor) carrying the declarations
     * @param javaName the Java-side name, used as the final fallback
     * @return the effective document field name
     * @throws MeiliMappingException if {@code @MeiliField.name} and an explicit
     *         {@code @JsonProperty} value are both present and differ
     */
    public static String docName(AccessibleObject member, String javaName) {
        String jsonName = null;
        JsonProperty jp = member.getAnnotation(JsonProperty.class);
        if (jp != null && !jp.value().isEmpty() && !JsonProperty.USE_DEFAULT_NAME.equals(jp.value())) {
            jsonName = jp.value();
        }
        MeiliField mf = member.getAnnotation(MeiliField.class);
        if (mf != null && !mf.name().isEmpty()) {
            if (jsonName != null && !jsonName.equals(mf.name())) {
                throw new MeiliMappingException("字段 " + javaName + " 的 @MeiliField.name("
                        + mf.name() + ") 与 @JsonProperty(" + jsonName + ") 冲突");
            }
            return mf.name();
        }
        return jsonName != null ? jsonName : javaName;
    }

    /**
     * Decides whether a type is a flattening leaf: primitives, strings, enums, numbers,
     * the common java.time/UUID shapes, arrays, collections and maps — plus everything
     * under the {@code java.} namespace — are leaves; only aggregate (non-{@code java.})
     * object types are expanded into dotted paths.
     *
     * @param type the declared field type
     * @return {@code true} if the field stops the nested walk
     */
    public static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type.isEnum()
                || Number.class.isAssignableFrom(type)
                || type == java.time.LocalDate.class
                || type == java.time.LocalDateTime.class
                || type == java.time.OffsetDateTime.class
                || type == java.time.Instant.class
                || type == java.util.UUID.class
                || type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || type.getName().startsWith("java.");
    }
}
