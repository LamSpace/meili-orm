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

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/** Matrix repository test-case entity. */
@MeiliDocument(indexName = "matrix_repo_books")
public class MatrixBook {

    /** Primary key (includes a > 2^53 precision sample). */
    @MeiliId
    public Long id;
    /** Renamed + searchable. */
    @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
    public String title;
    /** Filterable. */
    @MeiliField(filterable = true)
    public String genre;
    /** Filterable + sortable. */
    @MeiliField(filterable = true, sortable = true)
    public Double price;

    /** No-arg constructor for Jackson/reflection. */
    public MatrixBook() {
    }

    /**
     * @param id    primary key
     * @param title book title
     * @param genre genre
     * @param price price
     */
    public MatrixBook(Long id, String title, String genre, Double price) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.price = price;
    }
}
