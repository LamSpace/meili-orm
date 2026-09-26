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
package io.github.lamspace.meili.example.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliSetting;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Demo entity: a single class covering the full mapping surface.
 *
 * <p>Role declarations are the settings projection (unannotated = undeclared): {@code book_title}/
 * {@code overview} go to searchableAttributes (explicit order first), {@code author.city}/{@code tags}/
 * {@code genre}/{@code price} go to filterableAttributes (nested dotted paths flattened),
 * {@code price}/{@code publishedAt} go to sortableAttributes. rankingRules and the Chinese
 * stopwords pass through from {@code meili/books.json} and take precedence when merged with the
 * projection. {@code internalNote} is omitted from the document entirely via {@code @JsonIgnore}.
 *
 * @param id             primary key (Long; losslessness verified with a probe value above 2^53)
 * @param title          book title, document field name book_title, top search weight
 * @param overview       synopsis, secondary search field
 * @param author         nested author object (city participates in the filterable projection)
 * @param tags           tag array, filterable
 * @param genre          genre, filterable and facet demo
 * @param price          list price, filterable + sortable
 * @param publishedAt    first publication time, sortable
 * @param internalNote   internal note, never written to the MeiliSearch document
 */
@MeiliDocument(indexName = "books")
@MeiliSetting(settingPath = "classpath:meili/books.json")
public record Book(
        @MeiliId Long id,
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
        @MeiliField(searchable = true) String overview,
        Author author,
        @MeiliField(filterable = true) List<String> tags,
        @MeiliField(filterable = true) String genre,
        @MeiliField(filterable = true, sortable = true) Double price,
        @MeiliField(sortable = true) OffsetDateTime publishedAt,
        @JsonIgnore String internalNote) {
}
