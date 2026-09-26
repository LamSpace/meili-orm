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
