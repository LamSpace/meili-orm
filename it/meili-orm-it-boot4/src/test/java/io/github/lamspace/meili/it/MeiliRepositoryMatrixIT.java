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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Dual-generation matrix repository case: under the commons 4.0.3 (Boot 4 generation) runtime,
 * starter assembly + repository-module fallback auto-configuration + full-chain real-server
 * round-trips of derived/full-text/paginated/annotated queries, kept item-for-item consistent
 * with the Boot 3 side.
 *
 * <p>Byte-identical to the {@code it-boot3} file of the same name except for the two sentinel
 * expected values (matrix source dual-copy rule).
 */
@SpringBootTest(classes = ItApp.class)
class MeiliRepositoryMatrixIT {

    /** Boot-generation sentinel expectation (this module is 4). */
    private static final String EXPECTED_BOOT_MAJOR = "4.";

    /** commons-generation structural sentinel expectation: true iff the 4.0-only class org.springframework.data.core.PropertyPath exists. */
    private static final boolean EXPECT_GENERATION_4 = true;

    @DynamicPropertySource
    static void meiliProps(DynamicPropertyRegistry registry) {
        registry.add("meili.url", MeiliContainer::url);
        registry.add("meili.api-key", () -> MeiliContainer.MASTER_KEY);
        registry.add("meili.wait-task", () -> "true");
        registry.add("meili.index.auto-init", () -> "sync-settings");
        registry.add("meili.index.on-settings-drift", () -> "apply");
    }

    @Autowired
    MatrixBookRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new MatrixBook(9007199254740993L, "三体", "科幻", 59.0),
                new MatrixBook(2L, "沙丘", "科幻", 45.0),
                new MatrixBook(1L, "活着", "现实", 28.0)));
    }

    @Test
    void generationSentinels() {
        assertThat(SpringBootVersion.getVersion()).startsWith(EXPECTED_BOOT_MAJOR);
        boolean gen4;
        try {
            Class.forName("org.springframework.data.core.PropertyPath");
            gen4 = true;
        } catch (ClassNotFoundException e) {
            gen4 = false;
        }
        assertThat(gen4)
                .as("commons pin drift: org.springframework.data.core.PropertyPath presence=%s, expected=%s",
                        gen4, EXPECT_GENERATION_4)
                .isEqualTo(EXPECT_GENERATION_4);
    }

    @Test
    void autoConfigurationRegistersRepositoryWithoutEnableAnnotation() {
        assertThat(repository).isNotNull();
    }

    @Test
    void derivedQueriesRoundTripIdentically() {
        assertThat(repository.findByGenre("科幻")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L);
        assertThat(repository.findByTitleContaining("三体")).extracting(b -> b.id)
                .containsExactly(9007199254740993L);
        Page<MatrixBook> page = repository.findPageByGenreOrderByPriceAsc("科幻",
                PageRequest.of(0, 1));
        assertThat(page.getContent()).extracting(b -> b.price).containsExactly(45.0);
        assertThat(page.getTotalElements()).isPositive();
        assertThat(repository.expensive(30.0)).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L);
        assertThat(repository.findById(9007199254740993L))
                .hasValueSatisfying(b -> assertThat(b.title).isEqualTo("三体"));
    }
}
