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

import io.github.lamspace.meili.core.mapping.MeiliField;

/**
 * Nested value object: demonstrates dotted-path projection — {@code author.city} as a
 * filterableAttributes member, aligned with the MeiliSearch server's flattening semantics
 * for nested objects.
 *
 * @param name author name (not part of any role array)
 * @param city author's city, projected as the filterable field author.city
 */
public record Author(String name, @MeiliField(filterable = true) String city) {
}
