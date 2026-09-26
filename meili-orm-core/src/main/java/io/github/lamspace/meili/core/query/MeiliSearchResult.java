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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, strongly typed view of one MeiliSearch response: materialized hits plus the
 * envelope metadata the server actually returned (absent keys read as {@code null} /
 * empty maps, never defaults), plus the untouched {@linkplain #getRawJson() raw text}.
 *
 * <p>Materialization contract: each hit node is handed to the pluggable
 * {@link MeiliDocumentSerializer} as its own JSON string, so entity conversion — including
 * lossless Long primary keys beyond 2^53 — is the serializer's job exclusively. Envelope
 * metadata (all small numerals, booleans and strings) is parsed by a private Jackson
 * reader confined to this class; it never touches hit payloads.
 *
 * @param <T> entity type of the hits
 */
public final class MeiliSearchResult<T> {

    /** Envelope-only reader; kept private so no precision-lossy Map path leaks to hits. */
    private static final ObjectMapper ENVELOPE = new ObjectMapper();

    /** Typed hits, in server order. */
    private final List<T> hits;
    /** Estimated total match count (default pagination mode), else null. */
    private final Long estimatedTotalHits;
    /** Exact total hit count (paginated mode), else null. */
    private final Long totalHits;
    /** Offset-mode offset, else null. */
    private final Integer offset;
    /** Offset-mode limit, else null. */
    private final Integer limit;
    /** Page-mode page, else null. */
    private final Integer page;
    /** Page-mode total pages, else null. */
    private final Integer totalPages;
    /** Page-mode page size, else null. */
    private final Integer hitsPerPage;
    /** Facet value counts per requested facet attribute. */
    private final Map<String, Map<String, Integer>> facetDistribution;
    /** Facet numeric stats per facet attribute. */
    private final Map<String, Map<String, Double>> facetStats;
    /** Server-side processing time in milliseconds, else null. */
    private final Long processingTimeMs;
    /** Echo of the executed query text. */
    private final String query;
    /** Verbatim response body. */
    private final String rawJson;

    /**
     * Freezes one parsed response; only {@link #from} constructs results.
     *
     * @param hits             typed hits in server order
     * @param estimatedTotalHits estimated match count or {@code null}
     * @param totalHits        exact total or {@code null}
     * @param offset           offset echo or {@code null}
     * @param limit            limit echo or {@code null}
     * @param page             page echo or {@code null}
     * @param totalPages       total pages echo or {@code null}
     * @param hitsPerPage      page size echo or {@code null}
     * @param facetDistribution facet counts, possibly empty
     * @param facetStats       facet stats, possibly empty
     * @param processingTimeMs server time or {@code null}
     * @param query            query echo or {@code null}
     * @param rawJson          verbatim response body
     */
    private MeiliSearchResult(List<T> hits, Long estimatedTotalHits, Long totalHits,
                              Integer offset, Integer limit, Integer page, Integer totalPages,
                              Integer hitsPerPage, Map<String, Map<String, Integer>> facetDistribution,
                              Map<String, Map<String, Double>> facetStats, Long processingTimeMs,
                              String query, String rawJson) {
        this.hits = List.copyOf(hits);
        this.estimatedTotalHits = estimatedTotalHits;
        this.totalHits = totalHits;
        this.offset = offset;
        this.limit = limit;
        this.page = page;
        this.totalPages = totalPages;
        this.hitsPerPage = hitsPerPage;
        this.facetDistribution = Map.copyOf(facetDistribution);
        this.facetStats = Map.copyOf(facetStats);
        this.processingTimeMs = processingTimeMs;
        this.query = query;
        this.rawJson = rawJson;
    }

    /**
     * Parses one raw search response into a typed result.
     *
     * @param rawJson    verbatim server response JSON
     * @param type       entity class for each hit
     * @param serializer engine used to materialize each hit node
     * @param <T>        entity type
     * @return immutable result view
     * @throws MeiliOrmException when the envelope cannot be read or a hit fails conversion
     */
    public static <T> MeiliSearchResult<T> from(
            String rawJson, Class<T> type, MeiliDocumentSerializer serializer) {
        JsonNode root;
        try {
            root = ENVELOPE.readTree(rawJson);
        } catch (Exception e) {
            throw new MeiliOrmException("failed to parse search result envelope: " + type.getName(), e);
        }
        List<T> hits = new ArrayList<>();
        for (JsonNode hit : root.path("hits")) {
            hits.add(serializer.read(hit.toString(), type));
        }
        return new MeiliSearchResult<>(hits,
                asLong(root, "estimatedTotalHits"), asLong(root, "totalHits"),
                asInt(root, "offset"), asInt(root, "limit"),
                asInt(root, "page"), asInt(root, "totalPages"), asInt(root, "hitsPerPage"),
                asNestedInts(root.path("facetDistribution")), asNestedDoubles(root.path("facetStats")),
                asLong(root, "processingTimeMs"), asText(root, "query"), rawJson);
    }

    /**
     * Reads an optional integral envelope field.
     *
     * @param root  envelope tree
     * @param field key name
     * @return the value, or {@code null} when absent/null
     */
    private static Long asLong(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asLong();
    }

    /**
     * Reads an optional small-integral envelope field.
     *
     * @param root  envelope tree
     * @param field key name
     * @return the value, or {@code null} when absent/null
     */
    private static Integer asInt(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asInt();
    }

    /**
     * Reads an optional text envelope field.
     *
     * @param root  envelope tree
     * @param field key name
     * @return the value, or {@code null} when absent/null
     */
    private static String asText(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    /**
     * Converts an object-of-objects facet distribution node (possibly missing).
     *
     * @param node tree at {@code facetDistribution}
     * @return nested counts map, empty when the node is absent
     */
    private static Map<String, Map<String, Integer>> asNestedInts(JsonNode node) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(attr -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            node.get(attr).fields().forEachRemaining(e -> counts.put(e.getKey(), e.getValue().asInt()));
            out.put(attr, counts);
        });
        return out;
    }

    /**
     * Converts an object-of-objects facet stats node (possibly missing).
     *
     * @param node tree at {@code facetStats}
     * @return nested stats map, empty when the node is absent
     */
    private static Map<String, Map<String, Double>> asNestedDoubles(JsonNode node) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(attr -> {
            Map<String, Double> stats = new LinkedHashMap<>();
            node.get(attr).fields().forEachRemaining(e -> stats.put(e.getKey(), e.getValue().asDouble()));
            out.put(attr, stats);
        });
        return out;
    }

    /**
     * Returns the typed hits in server order.
     *
     * @return immutable hit list
     */
    public List<T> getHits() {
        return hits;
    }

    /**
     * Returns the estimated total match count of the default pagination mode.
     *
     * @return count, or {@code null} when the response used page pagination instead
     */
    public Long getEstimatedTotalHits() {
        return estimatedTotalHits;
    }

    /**
     * Returns the exact total hit count of the page pagination mode.
     *
     * @return count, or {@code null} when absent
     */
    public Long getTotalHits() {
        return totalHits;
    }

    /**
     * Returns the offset-mode offset echo.
     *
     * @return offset, or {@code null}
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Returns the offset-mode limit echo.
     *
     * @return limit, or {@code null}
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Returns the page-mode page echo.
     *
     * @return page, or {@code null}
     */
    public Integer getPage() {
        return page;
    }

    /**
     * Returns the page-mode total pages echo.
     *
     * @return total pages, or {@code null}
     */
    public Integer getTotalPages() {
        return totalPages;
    }

    /**
     * Returns the page-mode page size echo.
     *
     * @return hits per page, or {@code null}
     */
    public Integer getHitsPerPage() {
        return hitsPerPage;
    }

    /**
     * Returns facet value counts for the requested facets.
     *
     * @return nested map, empty when no facets were requested
     */
    public Map<String, Map<String, Integer>> getFacetDistribution() {
        return facetDistribution;
    }

    /**
     * Returns numeric facet stats for the requested facets.
     *
     * @return nested map, empty when absent
     */
    public Map<String, Map<String, Double>> getFacetStats() {
        return facetStats;
    }

    /**
     * Returns the server processing time.
     *
     * @return milliseconds, or {@code null} when absent
     */
    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    /**
     * Returns the echoed query text.
     *
     * @return query string, or {@code null} when absent
     */
    public String getQuery() {
        return query;
    }

    /**
     * Returns the verbatim response JSON — the documented escape hatch for anything the
     * typed view does not model.
     *
     * @return raw response text
     */
    public String getRawJson() {
        return rawJson;
    }

    @Override
    public String toString() {
        return "MeiliSearchResult[hits=" + hits.size() + ", query=" + query + "]";
    }
}
