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
package io.github.lamspace.meili.testcontainers.manual;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.testcontainers.MeiliSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Real-machine IT: the documentation's "manual bridge" sample in verbatim-runnable form —
 * default discovery resolves the application configuration, the container is bridged into a
 * {@link MeiliConnectionDetails} bean via a nested {@code @TestConfiguration}, the
 * properties-based details back off by default, and a save-then-read-by-id round trip succeeds.
 */
@SpringBootTest(properties = "meili.wait-task=true")
class ManualBridgeIT {

    /** Manually lifecycle-managed container: started on class load, reclaimed by Ryuk. */
    private static final MeiliSearchContainer MEILI = new MeiliSearchContainer();

    static {
        MEILI.start();
    }

    /** Data operations surface at the end of the wiring chain. */
    @Autowired
    private MeiliSearchOperations operations;

    /**
     * Completes a save-read round trip through the manually bridged wiring chain.
     */
    @Test
    void saveThenReadByIdRoundTripsThroughManuallyBridgedContainer() {
        operations.save(new ManualBridgeBook(77L, "手工桥接"));

        assertThat(operations.findById(77L, ManualBridgeBook.class))
                .hasValueSatisfying(book -> assertThat(book.title()).isEqualTo("手工桥接"));
    }

    /**
     * Manual bridge configuration: declaring a {@link MeiliConnectionDetails} bean makes the
     * default properties-based implementation back off, matching the documentation sample
     * verbatim.
     */
    @TestConfiguration
    static class BridgeConfig {

        /**
         * Bridges the static container's connection facts into a details bean.
         *
         * @return connection details delegating to the live container
         */
        @Bean
        MeiliConnectionDetails meiliConnectionDetails() {
            return new MeiliConnectionDetails() {
                @Override
                public String getUrl() {
                    return MEILI.getUrl();
                }

                @Override
                public String getApiKey() {
                    return MEILI.getApiKey();
                }
            };
        }
    }
}
