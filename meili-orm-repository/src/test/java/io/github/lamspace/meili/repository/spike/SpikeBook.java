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
package io.github.lamspace.meili.repository.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * spike sample entity (POJO shape): covers renamed properties, nested dot paths, opaque
 * collection leaves, boolean/temporal properties and {@code @JsonIgnore} exclusion.
 */
@MeiliDocument(indexName = "spike_books")
class SpikeBook {

    /** Primary key. */
    @MeiliId
    Long id;
    /** Renamed + searchable. */
    @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
    String title;
    /** searchable (leaf). */
    @MeiliField(searchable = true)
    String overview;
    /** filterable. */
    @MeiliField(filterable = true)
    String genre;
    /** filterable + sortable. */
    @MeiliField(filterable = true, sortable = true)
    Double price;
    /** Nested aggregate (expands into a dotted path). */
    SpikeAuthor author;
    /** Opaque collection leaf (filterable). */
    @MeiliField(filterable = true)
    List<String> tags;
    /** Boolean property (filterable). */
    @MeiliField(filterable = true)
    Boolean active;
    /** Temporal property (sortable). */
    @MeiliField(sortable = true)
    OffsetDateTime publishedAt;
    /** Excluded by Jackson; must not participate in query resolution. */
    @JsonIgnore
    String secret;
}
