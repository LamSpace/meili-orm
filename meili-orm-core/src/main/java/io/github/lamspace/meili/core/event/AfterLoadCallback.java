package io.github.lamspace.meili.core.event;

/**
 * Invoked on the <em>raw document JSON string</em> right after it is fetched from the
 * server and before deserialization — the only hook that can still see and rewrite the
 * stored document itself.
 *
 * @param <T> the entity type this callback applies to (concrete type argument, resolved at
 *            registration)
 */
public interface AfterLoadCallback<T> extends MeiliCallback {

    /**
     * Receives the raw document text.
     *
     * @param rawDocumentJson verbatim server document JSON
     * @param indexName       source index uid
     * @return the JSON to continue deserialization with (same string if unchanged)
     */
    String onAfterLoad(String rawDocumentJson, String indexName);
}
