package io.github.lamspace.meili.core.event;

/**
 * Invoked on an entity right after it has been deserialized from a stored document —
 * the final mutation point before the value reaches the caller.
 *
 * @param <T> the entity type this callback applies to (concrete type argument, resolved at
 *            registration)
 */
public interface AfterConvertCallback<T> extends MeiliCallback {

    /**
     * Receives the freshly materialized entity.
     *
     * @param entity    the deserialized entity
     * @param indexName source index uid
     * @return the entity to hand back to the caller
     */
    T onAfterConvert(T entity, String indexName);
}
