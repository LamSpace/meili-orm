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
