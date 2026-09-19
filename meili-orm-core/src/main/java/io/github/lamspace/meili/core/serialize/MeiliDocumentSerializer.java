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
