package io.github.lamspace.meili.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a MeiliSearch document entity and binds it to one index.
 *
 * <p>The index name is static for the first release; dynamic (per-call) index naming is
 * deliberately not part of this contract. Exactly one entity per index name is expected
 * across an application — the duplicate check itself is performed where the entity set is
 * assembled at startup, not by this annotation.
 *
 * <p>Applied to types only, retained at runtime, and read exclusively through
 * {@link MeiliPersistentEntity#of(Class)}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeiliDocument {

    /**
     * The MeiliSearch index this entity is stored in.
     *
     * @return the index uid; must be non-blank
     */
    String indexName();
}
