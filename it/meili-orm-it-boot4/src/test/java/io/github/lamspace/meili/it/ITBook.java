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

/**
 * Matrix round-trip entity: a Long primary key above 2^53 + role projection
 * (searchable/filterable/sortable).
 *
 * <p>The startup initializer (sync-settings + apply) builds the index and pushes settings per this
 * projection, then the full CRUD and search round-trip chain covers the Long precision contract.
 * One of the two shapes — record and POJO — is carried by this class.
 */
@MeiliDocument(indexName = "it_starter_books")
public record ITBook(
        @MeiliId Long id,
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
        @MeiliField(filterable = true, sortable = true) Double price) {
}
