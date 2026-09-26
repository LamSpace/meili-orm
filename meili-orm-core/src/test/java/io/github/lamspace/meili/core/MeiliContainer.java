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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Project-wide shared MeiliSearch test container (image pinned to v1.49.0, distributed
 * via the core test-jar).
 *
 * <p>Threading model: the singleton container starts in the static initializer at class
 * load time and is shared by every integration test in the same JVM; it is reclaimed by
 * Testcontainers Ryuk at JVM exit, never restarted per test.</p>
 *
 * <p>Hard prerequisites (declarative: missing ones fail fast with no silent skips):
 * a usable local Docker daemon and a local {@code getmeili/meilisearch:v1.49.0} image.
 * When the daemon is unavailable, the failure surfaces as
 * {@code NoClassDefFoundError}/{@code ExceptionInInitializerError} (Testcontainers
 * client initialization), which is an environment problem rather than a test defect.</p>
 *
 * <p>Readiness: {@code /health} returns HTTP 200. The master key is a fixed test value
 * valid only for the throwaway data inside this container and must never be used in
 * any non-test scenario.</p>
 */
public final class MeiliContainer {

    /** Master key inside the container (fixed test-only value). */
    public static final String MASTER_KEY = "masterKey-test-123456";

    /** Pinned image coordinate: tag exactly v1.49.0, never latest or a version range. */
    public static final String IMAGE = "getmeili/meilisearch:v1.49.0";

    /** Singleton container: random host port mapped to 7700, dev mode + fixed master key. */
    public static final GenericContainer<?> MEILI = new GenericContainer<>(
            DockerImageName.parse(IMAGE))
        .withExposedPorts(7700)
        .withEnv("MEILI_MASTER_KEY", MASTER_KEY)
        .withEnv("MEILI_ENV", "development")
        .waitingFor(Wait.forHttp("/health").forPort(7700).forStatusCode(200));

    static {
        MEILI.start();
    }

    private MeiliContainer() {
    }

    /**
     * Returns the base URL of the running container ({@code http://host:mappedPort})
     * for use by SDK {@code Config} or property injection.
     *
     * @return service address of the form {@code http://localhost:49152}
     */
    public static String url() {
        return "http://" + MEILI.getHost() + ":" + MEILI.getMappedPort(7700);
    }
}
