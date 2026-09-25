package io.github.lamspace.meili.autoconfigure.it.v1;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/** Phase-one entity of the drift IT: genre is filterable, price is not declared at all. */
@MeiliDocument(indexName = "it_init_books")
public class ITBook {

    /** Primary key. */
    @MeiliId public Long id;

    /** Searchable title. */
    @MeiliField(searchable = true, searchableOrder = 1) public String title;

    /** Filterable genre. */
    @MeiliField(filterable = true) public String genre;
}
