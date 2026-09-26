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
package io.github.lamspace.meili.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Matrix IT: the starter's assembly and CRUD round-trip on a real server (pinned v1.49.0 container).
 *
 * <p>This class is shared byte-for-byte between the two matrix modules, Boot 3.5.16 and 4.0.3
 * (the only differences are the version-sentinel expected values, and the generation a module
 * actually pins is self-evidenced by that sentinel): under both generations' classpaths, the
 * assembly chain, the raw read channel, and the Long precision contract must be equivalent. The
 * container is carried by core test-jar's {@link MeiliContainer} static singleton; the write path
 * is configured to wait for task completion, so read assertions are race-free.
 */
@SpringBootTest(classes = ItApp.class)
class MeiliStarterIT {

    /** Probe primary key above 2^53: any Gson/Double intermediary would lose precision here. */
    private static final long LOSSY_ABOVE_DOUBLE_ID = 9007199254740993L;

    @Autowired
    private MeiliSearchOperations operations;

    @Autowired
    private MeiliDocumentSerializer serializer;

    /**
     * Injects the pinned container's connection parameters and synchronous-write policy into the test context.
     *
     * @param registry the property registrar
     */
    @DynamicPropertySource
    static void meiliProperties(DynamicPropertyRegistry registry) {
        registry.add("meili.url", MeiliContainer::url);
        registry.add("meili.api-key", () -> MeiliContainer.MASTER_KEY);
        registry.add("meili.wait-task", () -> "true");
        registry.add("meili.index.auto-init", () -> "sync-settings");
        registry.add("meili.index.on-settings-drift", () -> "apply");
    }

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
     * Starter assembly + write/read/search round-trip: the default serializer is Jackson2; the
     * startup initializer creates the index; the Long primary key survives the full
     * save→findById→search chain bit-for-bit lossless.
     */
    @Test
    void starterWiresAndRoundTripsLongKeyedEntity() {
        assertThat(serializer).isInstanceOf(Jackson2DocumentSerializer.class);
        assertThat(operations.indexExists(ITBook.class)).isTrue();

        operations.save(new ITBook(LOSSY_ABOVE_DOUBLE_ID, "三体", 59.0));

        assertThat(operations.findById(LOSSY_ABOVE_DOUBLE_ID, ITBook.class))
                .hasValueSatisfying(b -> assertThat(b.id()).isEqualTo(LOSSY_ABOVE_DOUBLE_ID));

        MeiliSearchResult<ITBook> result = operations.search("三体", ITBook.class);
        assertThat(result.getHits())
                .extracting(ITBook::id)
                .contains(LOSSY_ABOVE_DOUBLE_ID);
    }
}
