package io.github.lamspace.meili.core.operations;

import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.core.exception.MeiliTaskTimeoutException;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.task.MeiliTask;
import java.util.List;
import java.util.Optional;

/**
 * Template-style facade over MeiliSearch document and task operations — the entry point
 * applications code against. Index resolution is always by entity class (the metamodel
 * owns the index name), and no transport-library type appears in any signature.
 *
 * <p>Write semantics: MeiliSearch processes every mutation asynchronously; without the
 * wait-task option a successful return means "accepted", not "applied" — read-your-write
 * requires the option (or an explicit {@link #awaitTask(int)} on the returned uid).
 *
 * <p>Implementations are thread-safe and shareable; they hold configuration, not state.
 */
public interface MeiliSearchOperations {

    /**
     * Upserts one entity (same primary key overwrites).
     *
     * @param entity entity with a non-null {@code @MeiliId} value
     * @param <T>    entity type
     * @return the entity after the write callback chain
     * @throws io.github.lamspace.meili.core.exception.MeiliOrmException on a null primary
     *         key or serialization failure
     * @throws MeiliIndexAccessException when the server rejects or transport fails
     */
    <T> T save(T entity);

    /**
     * Upserts many same-type entities as one request (single accepted task).
     *
     * @param entities entities, all of one entity type; empty is a no-op
     * @param <T>      entity type
     * @return the entities after their write callback chains
     * @throws io.github.lamspace.meili.core.exception.MeiliOrmException on mixed types,
     *         null primary keys, or serialization failure
     */
    <T> List<T> saveAll(Iterable<T> entities);

    /**
     * Fetches one document by primary key through the raw channel.
     *
     * @param id   primary key value (String or integer type); not {@code null}
     * @param type entity class
     * @param <T>  entity type
     * @return the entity, or empty when the document does not exist
     * @throws MeiliIndexAccessException when the index itself is missing
     */
    <T> Optional<T> findById(Object id, Class<T> type);

    /**
     * Fetches stored documents by criteria (no text matching).
     *
     * @param type  entity class
     * @param query staged fetch criteria
     * @param <T>   entity type
     * @return matching entities in server order
     */
    <T> List<T> findAll(Class<T> type, DocumentsFetchQuery query);

    /**
     * Deletes one document by primary key.
     *
     * @param id   primary key value; not {@code null}
     * @param type entity class (supplies the index)
     * @param <T>  entity type
     */
    <T> void deleteById(Object id, Class<T> type);

    /**
     * Deletes all documents of the entity's index, keeping the index and settings.
     *
     * @param type entity class
     * @param <T>  entity type
     */
    <T> void deleteAll(Class<T> type);

    /**
     * Counts stored documents via the server-side count endpoint.
     *
     * @param type entity class
     * @param <T>  entity type
     * @return exact document count
     */
    <T> long count(Class<T> type);

    /**
     * Blocks until a task reaches a terminal state, using the configured wait budget.
     *
     * @param taskUid task accepted by an earlier write
     * @throws MeiliTaskTimeoutException when the wait budget expires
     * @throws MeiliIndexAccessException when the task ends FAILED/CANCELED
     */
    void awaitTask(int taskUid);

    /**
     * Reads one task snapshot.
     *
     * @param taskUid task to observe
     * @return immutable core view of the task
     */
    MeiliTask getTask(int taskUid);
}
