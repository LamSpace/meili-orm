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
package io.github.lamspace.meili.sc;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import io.github.lamspace.meili.testcontainers.MeiliSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Matrix real-server IT (service-connection scenario 2, "the user's connection-details bean takes
 * precedence"): when the user's own {@link MeiliConnectionDetails} bean coexists with a
 * {@code @ServiceConnection} container, the bridge backs off and {@link Config} uses the user
 * bean's connection info.
 *
 * <p>Shared byte-for-byte with the same-named class in the Boot 4.0.3 matrix module (only the
 * version-sentinel expected value differs). The backoff takes effect under both generations via
 * meili-orm-testcontainers' auto-configuration guard (the backoff mechanism uses only
 * bean-definition attribute markers identical across both generations); this class is that
 * mechanism's dual-generation end-to-end proof.
 */
@SpringBootTest(classes = ServiceConnectionBackoffApp.class, properties = "meili.index.auto-init=none")
class MeiliServiceConnectionBackoffMatrixIT {

    /** The same mechanism takes over the container, but the bridging result should be superseded by the user's own bean (the container therefore needs no startup). */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** Assembly-result observation point: must carry the user bean's connection info. */
    @Autowired
    private Config config;

    /** Query surface for the post-backoff type-uniqueness assertion. */
    @Autowired
    private ApplicationContext context;

    /**
     * Version sentinel: proves this module actually runs on the Boot generation it is pinned to —
     * the matrix has not silently switched generations.
     */
    @Test
    void classpathIsThePinnedGeneration() {
        assertThat(SpringBootVersion.getVersion())
                .as("matrix version-pin drift: this module's running classpath does not match the pinned version")
                .startsWith("3.");
    }

    /**
     * The user's own bean takes precedence: Config lands on the user's URL/key and the bridge
     * leaves no same-type bean behind.
     */
    @Test
    void userConnectionDetailsBeanWinsOverTheBridge() {
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(ServiceConnectionBackoffApp.USER_URL);
        assertThat(config.getApiKey()).isEqualTo(ServiceConnectionBackoffApp.USER_KEY);
        assertThat(context.getBeanNamesForType(MeiliConnectionDetails.class))
                .containsExactly("userMeiliConnectionDetails");
    }
}
