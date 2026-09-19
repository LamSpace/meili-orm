package io.github.lamspace.meili.core.internal;

import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.core.exception.MeiliTaskTimeoutException;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.task.MeiliTask;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The transport SPI of core: every interaction with a MeiliSearch server goes through
 * this interface, making it the single legal crossing point of official-client types
 * (implementations live in this package; the public API only ever sees strings, IR
 * objects and core views).
 *
 * <p>Contract shared by all implementations: documents cross as JSON strings in both
 * directions (never transport model objects); all writes return the accepted task uid and
 * are asynchronous until awaited; a missing document reads as {@link Optional#empty()}
 * while a missing <em>index</em> is a {@link MeiliIndexAccessException}; every failure is
 * translated into the public exception hierarchy.
 *
 * <p>Internal SPI — not part of the public API and may evolve between minor releases.
 */
public interface MeiliRawGateway {

    /**
     * Upserts a JSON array (or single-object string) of documents.
     *
     * @param indexUid      target index
     * @param documentsJson document JSON array text
     * @return accepted task uid
     */
    int updateDocuments(String indexUid, String documentsJson);

    /**
     * Fetches one stored document verbatim.
     *
     * @param indexUid source index
     * @param id       primary key as string
     * @return the raw document JSON, or empty when the document does not exist
     */
    Optional<String> fetchRawDocument(String indexUid, String id);

    /**
     * Fetches stored documents matching criteria (filter/fields/sort/pagination).
     *
     * @param indexUid source index
     * @param query    fetch IR; every staged field is honored
     * @return raw JSON of each matching document, server order
     */
    List<String> fetchRawDocuments(String indexUid, DocumentsFetchQuery query);

    /**
     * Counts stored documents via the dedicated count endpoint.
     *
     * @param indexUid target index
     * @return exact document count, never via a floating-point path
     */
    long count(String indexUid);

    /**
     * Executes a search and returns the untouched response JSON.
     *
     * @param indexUid index to search
     * @param query    fully staged IR query
     * @return verbatim search response JSON
     */
    String rawSearch(String indexUid, MeiliQuery query);

    /**
     * Reports index existence.
     *
     * @param indexUid index to probe
     * @return {@code true} when the index exists
     */
    boolean indexExists(String indexUid);

    /**
     * Creates an index with the given primary key.
     *
     * @param indexUid  new index uid
     * @param primaryKey primary key field name
     * @return accepted task uid
     */
    int createIndex(String indexUid, String primaryKey);

    /**
     * Deletes an index.
     *
     * @param indexUid index to drop
     * @return accepted task uid
     */
    int deleteIndex(String indexUid);

    /**
     * PATCHes the settings document (server semantics: given keys merge, absent keys
     * stay).
     *
     * @param indexUid     target index
     * @param settingsJson settings document produced by the projection
     * @return accepted task uid
     */
    int updateSettings(String indexUid, String settingsJson);

    /**
     * Reads the current settings exactly as the server reports them.
     *
     * @param indexUid target index
     * @return raw settings JSON — the drift-diff ground truth
     */
    String getSettings(String indexUid);

    /**
     * Deletes one document by primary key.
     *
     * @param indexUid target index
     * @param id       primary key as string
     * @return accepted task uid
     */
    int deleteDocument(String indexUid, String id);

    /**
     * Deletes every document, keeping the index and its settings.
     *
     * @param indexUid target index
     * @return accepted task uid
     */
    int deleteAllDocuments(String indexUid);

    /**
     * Blocks until the task reaches a terminal state.
     *
     * @param taskUid  task to observe
     * @param timeout  wait budget; on expiry the wait is abandoned, the task is not
     * @throws MeiliTaskTimeoutException  when the budget expires first
     * @throws MeiliIndexAccessException when the task ends FAILED/CANCELED (server error
     *         detail included) or cannot be observed
     */
    void awaitTask(int taskUid, Duration timeout);

    /**
     * Reads one task snapshot.
     *
     * @param taskUid task to fetch
     * @return immutable core view of the task
     */
    MeiliTask getTask(int taskUid);
}
