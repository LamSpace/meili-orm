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

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * spike sample entity (record shape): verifies the structural bridge resolves record components field-wise.
 *
 * @param id    primary key
 * @param title renamed property
 * @param genre filterable property
 */
@MeiliDocument(indexName = "spike_record_books")
record SpikeRecordBook(
        @MeiliId Long id,
        @MeiliField(name = "record_title", searchable = true) String title,
        @MeiliField(filterable = true) String genre) {
}
