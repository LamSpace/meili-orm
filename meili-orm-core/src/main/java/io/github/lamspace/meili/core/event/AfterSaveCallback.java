package io.github.lamspace.meili.core.event;

/**
 * Invoked once a write request has been accepted for an entity (after any configured
 * task wait), for post-save bookkeeping.
 *
 * @param <T> the entity type this callback applies to (concrete type argument, resolved at
 *            registration)
 */
public interface AfterSaveCallback<T> extends MeiliCallback {

    /**
     * Receives the saved entity.
     *
     * @param entity    the entity as written
     * @param indexName target index uid
     * @return the entity to hand back to the caller (chaining point for decorators)
     */
    T onAfterSave(T entity, String indexName);
}
