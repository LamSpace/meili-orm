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
package io.github.lamspace.meili.core.serialize;

/**
 * The pluggable entity ↔ document-JSON boundary of meili-orm: everything the framework
 * stores in or reads from MeiliSearch passes through these two methods as plain strings.
 *
 * <p>The deliberately two-method surface carries no JSON-library types on purpose — a
 * different engine (e.g. Jackson 3) can replace the default implementation wholesale
 * without touching any caller, which is what keeps the core usable across Spring Boot
 * generations whose default JSON stack differs.
 *
 * <p>Contract for implementations: {@link #write(Object)} must honor the mapping naming
 * rules (a {@code @MeiliField.name} rename is the document field name) and round-trip
 * losslessly for Long primary keys beyond 2^53; failures surface as
 * {@link io.github.lamspace.meili.core.exception.MeiliOrmException}, never as
 * engine-specific exceptions.
 */
public interface MeiliDocumentSerializer {

    /**
     * Serializes one entity (or a plain value) into its document JSON string.
     *
     * @param document the value to serialize; {@code null} is not a valid document
     * @return the JSON text to send to MeiliSearch
     * @throws io.github.lamspace.meili.core.exception.MeiliOrmException on any conversion failure
     */
    String write(Object document);

    /**
     * Deserializes one document JSON string into the target type.
     *
     * @param json the raw document text exactly as stored server-side
     * @param type the target class (POJO or record)
     * @param <T>  target type
     * @return the materialized object
     * @throws io.github.lamspace.meili.core.exception.MeiliOrmException on any conversion failure
     */
    <T> T read(String json, Class<T> type);
}
