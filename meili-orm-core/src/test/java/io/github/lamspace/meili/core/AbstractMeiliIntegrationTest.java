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
package io.github.lamspace.meili.core;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;

/**
 * Base class for MeiliSearch real-container integration tests: subclasses obtain a
 * configured SDK client pointing at the shared container via {@link #client()},
 * without assembling connection parameters themselves.
 *
 * <p>Stateless, no threading constraints; each call returns a new {@code Client} instance
 * (construction costs no network I/O) pointing at the same container. Depends on the core
 * module's test-scoped Testcontainers and a local Docker daemon.</p>
 */
public abstract class AbstractMeiliIntegrationTest {

    /**
     * Builds an SDK client pointing at the shared container (default GsonJsonHandler wiring).
     *
     * @return a {@link Client} configured with the container URL and the test master key
     */
    protected static Client client() {
        return new Client(new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY));
    }
}
