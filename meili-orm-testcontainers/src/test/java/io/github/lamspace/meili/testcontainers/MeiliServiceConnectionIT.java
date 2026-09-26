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
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real-machine IT (service-connection scenario 1, "the container joins the context and
 * completes the round trip"): a static field annotated {@code @ServiceConnection} makes the
 * bridge register a {@link MeiliConnectionDetails}, the assembled SDK {@link Config} carries
 * the container URL and key (observable before building), and a save-then-read-by-id round
 * trip succeeds.
 *
 * <p>The context declares no {@code meili.url}/{@code meili.api-key} properties at all — the
 * bridged container is the sole source of connection information, which is exactly the
 * acceptance surface of the "one annotation" syntax sugar. The write path waits for the task
 * to reach a terminal state so the read assertions are race-free; the index is created at
 * startup under create-if-missing.
 */
@SpringBootTest(classes = BridgeApp.class, properties = "meili.wait-task=true")
class MeiliServiceConnectionIT {

    /**
     * Container taken over by service connection: reused via the static field, started once
     * for the cached context's lifetime.
     */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /**
     * Connection details bean produced by the bridge (the properties-based implementation
     * should back off because of it).
     */
    @Autowired
    private MeiliConnectionDetails details;

    /** SDK configuration built from the bridged details: observe where the URL and key land before building. */
    @Autowired
    private Config config;

    /** Data operations surface at the end of the wiring chain: entry point of the round-trip reads and writes. */
    @Autowired
    private MeiliSearchOperations operations;

    /**
     * The bridge bean's URL/key match the container's actual mapped port and configured key,
     * and the Config carries the same values.
     */
    @Test
    void configIsBuiltFromTheLiveContainer() {
        assertThat(details.getUrl()).isEqualTo(CONTAINER.getUrl());
        assertThat(details.getApiKey()).isEqualTo(CONTAINER.getApiKey());
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(CONTAINER.getUrl());
        assertThat(config.getApiKey()).isEqualTo(CONTAINER.getApiKey());
    }

    /**
     * Save then read-by-id round trip succeeds (real machine, through the wiring chain, zero
     * hand-written connection configuration).
     */
    @Test
    void saveThenReadByIdRoundTripsThroughTheContainer() {
        operations.save(new BridgeBook(42L, "桥接往返"));

        assertThat(operations.findById(42L, BridgeBook.class))
                .hasValueSatisfying(book -> assertThat(book.title()).isEqualTo("桥接往返"));
    }
}
