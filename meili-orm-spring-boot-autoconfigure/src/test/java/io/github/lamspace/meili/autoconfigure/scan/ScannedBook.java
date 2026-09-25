package io.github.lamspace.meili.autoconfigure.scan;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;

/** Fixture entity discovered by classpath scanning in {@code MeiliEntityScannerTest}. */
@MeiliDocument(indexName = "scanned_books")
public record ScannedBook(@MeiliId Long id, String title) {
}
