package io.github.lamspace.meili.core.mapping;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Parse-once cache of entity metamodels. The single entry point through which all core
 * components resolve an entity's mapping: the underlying map guarantees exactly one
 * {@link MeiliPersistentEntity#of(Class)} run per class even under concurrent first
 * access, and successful parses are reused for the context's lifetime.
 *
 * <p>Thread model: safe for concurrent use. {@link #getEntity(Class)} blocks only other
 * threads requesting the same class while parsing runs (standard
 * {@link ConcurrentHashMap#computeIfAbsent} segment semantics). A failed parse is not
 * cached: every call on an invalid entity re-attempts and re-throws, so startup
 * fail-fast behavior survives repeated lookups.
 *
 * <p>Caller duty: entities resolved here are assumed immutable in their mapping
 * declarations; changing annotations between calls is not a supported scenario.
 */
public final class MeiliMappingContext {

    /** Class → metamodel cache. */
    private final ConcurrentMap<Class<?>, MeiliPersistentEntity> entities = new ConcurrentHashMap<>();

    /** Creates an empty context; entities are parsed lazily on first lookup. */
    public MeiliMappingContext() {
    }

    /**
     * Returns the metamodel for an entity class, parsing it on first request.
     *
     * @param type the annotated entity class
     * @return the cached immutable metamodel (same instance on every call)
     * @throws MeiliMappingException when the class violates the mapping contract
     */
    public MeiliPersistentEntity getEntity(Class<?> type) {
        return entities.computeIfAbsent(type, MeiliPersistentEntity::of);
    }
}
