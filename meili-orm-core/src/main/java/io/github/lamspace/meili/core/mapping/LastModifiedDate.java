package io.github.lamspace.meili.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a property as the last-modification timestamp of the document. {@code save}/
 * {@code saveAll} overwrite it unconditionally with the write instant on every call,
 * just before {@code BeforeConvertCallback} runs — including on a first save, where it
 * coincides with the {@link CreatedDate} fill.
 *
 * <p>Applicable to fields only (record components propagate it to their backing field).
 * The declared type must be one of {@code Instant}, {@code OffsetDateTime},
 * {@code ZonedDateTime}, {@code LocalDateTime}, {@code long}, {@code Long} — anything
 * else fails entity parsing with {@code MeiliMappingException}. The annotated property
 * otherwise behaves as a normal document field: it keeps its projection name, is
 * serialized like any other member, and carries settings roles only if it additionally
 * declares them via {@link MeiliField}.
 *
 * @see CreatedDate
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LastModifiedDate {
}
