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
package io.github.lamspace.meili.core.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The settings document derived from one entity: the four role arrays projected from
 * field annotations plus every whitelist-checked passthrough key. A {@code null} array
 * means "not declared — leave the server default alone" (arrays are never emitted empty);
 * an empty {@code passthrough} map means no settings file was declared.
 *
 * <p>Record components hold the <em>projection</em> values; when a passthrough file
 * declares one of the four arrays, {@link #toJson()} — the transport form and the diff
 * baseline — resolves the conflict with passthrough precedence at that key's fixed
 * position.
 *
 * @param searchableAttributes projected searchable paths (explicit order first, then
 *                             lexicographic), or {@code null} when none declared
 * @param filterableAttributes projected filterable paths in lexicographic order, nullable
 * @param sortableAttributes   projected sortable paths in lexicographic order, nullable
 * @param displayedAttributes  projected displayed paths in lexicographic order, nullable;
 *                             once present the server hides every field not listed
 * @param passthrough          whitelist-validated settings-file keys merged in
 *                             declaration order (later files override earlier ones)
 */
public record ProjectedSettings(List<String> searchableAttributes, List<String> filterableAttributes,
                                List<String> sortableAttributes, List<String> displayedAttributes,
                                Map<String, Object> passthrough) {

    /** Format-stable writer: fixed key order plus Jackson's default pretty printing. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The four role-array keys, in their canonical output order. */
    private static final List<String> ROLE_KEYS = List.of(
            "searchableAttributes", "filterableAttributes", "sortableAttributes", "displayedAttributes");

    /**
     * Reports whether this entity declares anything to push.
     *
     * @return {@code true} when at least one role array is projected or a passthrough
     *         key exists; {@code false} means the settings step is skipped entirely
     */
    public boolean hasAny() {
        return searchableAttributes != null || filterableAttributes != null
                || sortableAttributes != null || displayedAttributes != null
                || !passthrough.isEmpty();
    }

    /**
     * Serializes the effective settings document with byte-stable formatting: role keys in
     * canonical order (omitted when not declared, substituted by passthrough when that
     * file wins), then remaining passthrough keys in lexicographic order.
     *
     * <p>Byte stability is a contract, not an accident: settings drift-diffing and
     * golden-file tests compare this output verbatim.
     *
     * @return the pretty-printed settings JSON
     * @throws MeiliOrmException if the document cannot be serialized
     */
    public String toJson() {
        if (!hasAny()) {
            return "{}";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, List<String>> roleValues = Map.of(
                "searchableAttributes", orEmpty(searchableAttributes),
                "filterableAttributes", orEmpty(filterableAttributes),
                "sortableAttributes", orEmpty(sortableAttributes),
                "displayedAttributes", orEmpty(displayedAttributes));
        for (String key : ROLE_KEYS) {
            if (passthrough.containsKey(key)) {
                out.put(key, passthrough.get(key));
            } else if (!roleValues.get(key).isEmpty()) {
                out.put(key, roleValues.get(key));
            }
        }
        new TreeMap<>(passthrough).forEach((key, value) -> {
            if (!ROLE_KEYS.contains(key)) {
                out.put(key, value);
            }
        });
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (Exception e) {
            throw new MeiliOrmException("failed to serialize settings projection", e);
        }
    }

    /**
     * Null-safe view of a projected role list.
     *
     * @param list role list, possibly {@code null}
     * @return the list or an empty stand-in
     */
    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
