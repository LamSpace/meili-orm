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
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import io.github.lamspace.meili.serialize.jackson3.Jackson3DocumentSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boot 4 + Jackson 3 opt-in matrix IT: real-server end-to-end proof of the optional serialization
 * module's "add the dependency and it takes over".
 *
 * <p>Reuses the baseline Boot4 matrix's {@link ItApp}/{@link ITBook} test shell (test-jar); the
 * only difference from the baseline is that meili-orm-serializer-jackson3 is present on this
 * module's classpath — auto-configuration ordering registers the Jackson3 serializer ahead of the
 * data layer. Asserts the takeover is in effect and the Long precision round-trip does not regress
 * because of it; the "no takeover when the class is absent" branch is proven by the baseline
 * it-boot4 and it-boot3 matrices and is not repeated in this module.
 */
@SpringBootTest(classes = ItApp.class)
class Jackson3WiringIT {

    /** Probe primary key above 2^53: the Jackson3 channel must likewise stay bit-for-bit lossless. */
    private static final long LOSSY_ABOVE_DOUBLE_ID = 9007199254740993L;

    @Autowired
    private MeiliSearchOperations operations;

    @Autowired
    private MeiliDocumentSerializer serializer;

    /**
     * Context properties mirroring the baseline matrix: pinned container + synchronous write + sync-settings/apply.
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
     * Takeover sentinel: with the class on the classpath the serializer becomes the Jackson3
     * implementation (the dependency-absent branch is covered by the baseline matrix).
     */
    @Test
    void serializerIsTakenOverByJackson3() {
        assertThat(SpringBootVersion.getVersion())
                .as("matrix version-pin drift: this module's running classpath does not match the pinned version")
                .startsWith("4.");
        assertThat(serializer).isInstanceOf(Jackson3DocumentSerializer.class);
    }

    /**
     * Real-server CRUD round-trip through the Jackson3 channel: the entity is written and read via
     * the raw channel, Long primary key bit-for-bit lossless.
     */
    @Test
    void jackson3ChannelRoundTripsAgainstRealServer() {
        operations.save(new ITBook(LOSSY_ABOVE_DOUBLE_ID, "活着", 26.0));

        assertThat(operations.findById(LOSSY_ABOVE_DOUBLE_ID, ITBook.class))
                .hasValueSatisfying(b -> assertThat(b.id()).isEqualTo(LOSSY_ABOVE_DOUBLE_ID));

        MeiliSearchResult<ITBook> result = operations.search("活着", ITBook.class);
        assertThat(result.getHits())
                .extracting(ITBook::id)
                .contains(LOSSY_ABOVE_DOUBLE_ID);
    }
}
