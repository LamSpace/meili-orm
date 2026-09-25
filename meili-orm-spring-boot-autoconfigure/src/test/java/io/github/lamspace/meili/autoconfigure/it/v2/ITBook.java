package io.github.lamspace.meili.autoconfigure.it.v2;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * Phase-two entity of the drift IT: same index as v1 but with {@code price} filterable added,
 * which is exactly the drift (and server-side rebuild trigger) the apply phase must observe.
 */
@MeiliDocument(indexName = "it_init_books")
public class ITBook {

    /** Primary key. */
    @MeiliId public Long id;

    /** Searchable title. */
    @MeiliField(searchable = true, searchableOrder = 1) public String title;

    /** Filterable genre. */
    @MeiliField(filterable = true) public String genre;

    /** Newly filterable price — the drift source. */
    @MeiliField(filterable = true) public Double price;
}
