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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a property as the creation timestamp of the document. {@code save}/{@code saveAll}
 * fill it on the write path just before {@code BeforeConvertCallback}: the current value
 * is kept when non-empty, and only an empty value ({@code null} for object types,
 * {@code 0} for primitive {@code long}) is replaced by the write instant.
 *
 * <p>MeiliSearch has no server-side timestamp and its upsert cannot distinguish an insert
 * from an update, so this is a deliberately approximate, client-side "created" semantics:
 * a reloaded entity keeps its first filled value; a hand-supplied value is never rewritten.
 *
 * <p>Applicable to fields only (record components propagate it to their backing field).
 * The declared type must be one of {@code Instant}, {@code OffsetDateTime},
 * {@code ZonedDateTime}, {@code LocalDateTime}, {@code long}, {@code Long} — anything
 * else fails entity parsing with {@code MeiliMappingException}. The annotated property
 * otherwise behaves as a normal document field: it keeps its projection name, is
 * serialized like any other member, and carries settings roles only if it additionally
 * declares them via {@link MeiliField}.
 *
 * @see LastModifiedDate
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreatedDate {
}
