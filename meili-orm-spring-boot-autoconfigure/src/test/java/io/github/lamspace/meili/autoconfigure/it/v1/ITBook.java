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
