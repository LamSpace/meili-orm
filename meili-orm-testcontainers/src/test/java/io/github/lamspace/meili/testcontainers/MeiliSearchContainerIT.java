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
package io.github.lamspace.meili.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-machine IT: default-container health readiness and image/key overrides hold against
 * live MeiliSearch instances.
 *
 * <p>Verdict surface: {@code /health} returning 200 means ready (the container wait
 * condition), and protected endpoints authenticate against the configured master key (200
 * with the right key, 403 with the wrong one), proving the key really was injected. Each
 * scenario starts and stops its own throwaway container; Ryuk reclaims them.
 */
class MeiliSearchContainerIT {

    /** JDK HTTP client: asserts health and authentication behavior without extra test dependencies. */
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Scenario "default container becomes healthy": once ready the URL serves the health
     * endpoint and authentication uses the configured master key.
     */
    @Test
    void defaultContainerBecomesHealthyAndAuthenticatesWithMasterKey() {
        try (MeiliSearchContainer container = new MeiliSearchContainer()) {
            container.start();

            assertThat(status(container, container.getUrl() + "/health", null)).isEqualTo(200);
            assertThat(status(container, container.getUrl() + "/indexes", container.getApiKey())).isEqualTo(200);
            assertThat(status(container, container.getUrl() + "/indexes", "wrong-key")).isEqualTo(403);
        }
    }

    /**
     * Scenario "overrides take effect": the explicit-image construction path and a custom key
     * are confirmed by server behavior on a live instance (custom key authenticates, default
     * key is rejected).
     */
    @Test
    void overriddenImageAndKeyTakeEffectOnLiveContainer() {
        DockerImageName image = DockerImageName.parse("getmeili/meilisearch:v1.49.0");
        try (MeiliSearchContainer container =
                     new MeiliSearchContainer(image).withMasterKey("override-key-live")) {
            container.start();

            assertThat(container.getDockerImageName()).isEqualTo(image.asCanonicalNameString());
            assertThat(container.getApiKey()).isEqualTo("override-key-live");
            assertThat(status(container, container.getUrl() + "/indexes", "override-key-live")).isEqualTo(200);
            assertThat(status(container, container.getUrl() + "/indexes",
                    MeiliSearchContainer.DEFAULT_MASTER_KEY)).isEqualTo(403);
        }
    }

    /**
     * Sends a GET and returns the response status code.
     *
     * @param container target container (kept alive for the duration of the request)
     * @param url       full request URL
     * @param apiKey    bearer key; {@code null} means no authorization header
     * @return the HTTP status code
     */
    private static int status(MeiliSearchContainer container, String url, String apiKey) {
        assertThat(container.isRunning()).as("container must be running").isTrue();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (apiKey != null) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
