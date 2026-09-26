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
