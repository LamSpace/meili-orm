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

import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Real-machine IT (service-connection scenario 2, "the user connection details bean wins"):
 * when the test context declares both a user-owned {@link MeiliConnectionDetails} bean and a
 * {@code @ServiceConnection} container, the bridge backs off and the {@link Config} uses the
 * user bean's connection information.
 *
 * <p>The backoff acceptance surface is the final wiring facts: the Config's URL/key come from
 * the user bean, and exactly one {@link MeiliConnectionDetails} instance — the user's — remains
 * in the context (the bridge bean leaves no residue, avoiding by-type injection ambiguity
 * downstream).
 */
@SpringBootTest(classes = BridgeBackoffApp.class, properties = "meili.index.auto-init=none")
class MeiliServiceConnectionBackoffIT {

    /** Container taken over by the same mechanism, whose bridge result should be superseded by the user-owned bean. */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** Observation point of the wiring result: must carry the user bean's connection information. */
    @Autowired
    private Config config;

    /** Lookup surface for the post-backoff type-uniqueness assertion. */
    @Autowired
    private ApplicationContext context;

    /**
     * The user-owned bean wins: the Config lands on the user URL/key and the bridge leaves no
     * bean of the type behind.
     */
    @Test
    void userConnectionDetailsBeanWinsOverTheBridge() {
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(BridgeBackoffApp.USER_URL);
        assertThat(config.getApiKey()).isEqualTo(BridgeBackoffApp.USER_KEY);
        assertThat(context.getBeanNamesForType(MeiliConnectionDetails.class))
                .containsExactly("userMeiliConnectionDetails");
    }
}
