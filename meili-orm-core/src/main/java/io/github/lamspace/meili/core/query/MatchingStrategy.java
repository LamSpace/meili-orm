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
package io.github.lamspace.meili.core.query;

/**
 * Word-matching strategy applied to query terms, mirroring the server parameter of the
 * same name.
 */
public enum MatchingStrategy {

    /** Every query term must appear in a matching document. */
    ALL,

    /** The last query term must appear; earlier terms only rank. */
    LAST,

    /**
     * Match as many words as possible, prioritizing the rarest terms (default server
     * behavior).
     */
    FREQUENCY,
}
