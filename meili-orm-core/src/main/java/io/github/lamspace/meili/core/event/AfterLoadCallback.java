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
