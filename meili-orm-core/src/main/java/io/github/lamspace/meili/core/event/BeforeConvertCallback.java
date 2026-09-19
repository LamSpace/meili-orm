package io.github.lamspace.meili.core.event;

/**
 * Invoked on an entity just before it is serialized for a write — the place to normalize
 * or stamp data while it is still a mutable object.
 *
 * @param <T> the entity type this callback applies to (must be a concrete type argument;
 *            it is resolved reflectively at registration)
 */
public interface BeforeConvertCallback<T> extends MeiliCallback {

    /**
     * Receives the about-to-be-written entity.
     *
     * @param entity     the current entity state
     * @param indexName  target index uid
     * @return the entity to continue with (may be the same or a new instance)
     */
    T onBeforeConvert(T entity, String indexName);
}
