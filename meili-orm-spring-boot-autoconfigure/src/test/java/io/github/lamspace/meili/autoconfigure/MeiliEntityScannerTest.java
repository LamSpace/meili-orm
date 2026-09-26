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
package io.github.lamspace.meili.autoconfigure;

import io.github.lamspace.meili.autoconfigure.scan.ScannedBook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MeiliEntityScanner}: annotation discovery, deduplication and stable
 * ordering across the given base packages.
 */
class MeiliEntityScannerTest {

    /** The fixture package holding exactly one {@code @MeiliDocument} type. */
    private static final String FIXTURE_PACKAGE = "io.github.lamspace.meili.autoconfigure.scan";

    @Test
    void discoversAnnotatedEntitiesOnly() {
        assertThat(MeiliEntityScanner.scanPackages(List.of(FIXTURE_PACKAGE)))
                .containsExactly(ScannedBook.class);
    }

    @Test
    void overlappingPackagesDeduplicate() {
        List<Class<?>> found = MeiliEntityScanner.scanPackages(List.of(FIXTURE_PACKAGE, FIXTURE_PACKAGE));
        assertThat(found).containsExactly(ScannedBook.class);
    }

    @Test
    void unknownAndEmptyPackageListsYieldEmpty() {
        assertThat(MeiliEntityScanner.scanPackages(List.of("no.such.package.exists"))).isEmpty();
        assertThat(MeiliEntityScanner.scanPackages(List.of())).isEmpty();
    }
}
