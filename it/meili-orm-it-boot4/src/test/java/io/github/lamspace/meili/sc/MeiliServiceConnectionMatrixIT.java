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
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.testcontainers.MeiliSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Matrix real-server IT (service-connection scenario 1): a {@link MeiliSearchContainer} declared
 * via {@code @ServiceConnection} is bridged into a {@link Config} pointing at the live container,
 * and after saving, a read-by-primary-key round-trip succeeds.
 *
 * <p>This class is shared byte-for-byte with the same-named class in the Boot 4.0.3 matrix module
 * (the only difference is the version-sentinel expected value, and the generation a module
 * actually pins is self-evidenced by that sentinel): one copy of meili-orm-testcontainers bytecode
 * must produce consistent bridge behavior under both generations' classpaths — end-to-end
 * evidence of the single-module dual-package shape.
 */
@SpringBootTest(classes = ServiceConnectionApp.class, properties = "meili.wait-task=true")
class MeiliServiceConnectionMatrixIT {

    /** Container taken over by the service connection: held in a static field for reuse, started once per context-cache lifetime. */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** Connection-details bean produced by the bridge. */
    @Autowired
    private MeiliConnectionDetails details;

    /** SDK config built from the bridged details: observe the URL and key landing points before construction. */
    @Autowired
    private Config config;

    /** Data-operation surface at the end of the assembly chain. */
    @Autowired
    private MeiliSearchOperations operations;

    /**
     * Version sentinel: proves this module actually runs on the Boot generation it is pinned to —
     * the matrix has not silently switched generations.
     */
    @Test
    void classpathIsThePinnedGeneration() {
        assertThat(SpringBootVersion.getVersion())
                .as("matrix version-pin drift: this module's running classpath does not match the pinned version")
                .startsWith("4.");
    }

    /**
     * The bridge bean's URL/key match the container's actual mapped port and configured key, and
     * Config carries the same values.
     */
    @Test
    void configIsBuiltFromTheLiveContainer() {
        assertThat(details.getUrl()).isEqualTo(CONTAINER.getUrl());
        assertThat(details.getApiKey()).isEqualTo(CONTAINER.getApiKey());
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(CONTAINER.getUrl());
        assertThat(config.getApiKey()).isEqualTo(CONTAINER.getApiKey());
    }

    /**
     * After saving, the read-by-primary-key round-trip succeeds (real server, through the
     * assembly chain, zero hand-written connection configuration).
     */
    @Test
    void saveThenReadByIdRoundTripsThroughTheContainer() {
        operations.save(new ServiceConnectionBook(7L, "矩阵桥接往返"));

        assertThat(operations.findById(7L, ServiceConnectionBook.class))
                .hasValueSatisfying(book -> assertThat(book.title()).isEqualTo("矩阵桥接往返"));
    }
}
