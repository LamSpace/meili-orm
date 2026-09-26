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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * meili-orm's own search request IR — the single vocabulary in which application code
 * expresses queries, deliberately independent of any transport request type (the same IR
 * will back derived-query methods later).
 *
 * <p>Shape: a mutable, chaining builder. Instances are <em>not</em> thread-safe; one
 * query object belongs to one calling thread and should be fully staged before it reaches
 * the operations layer.
 *
 * <p>Every IR concept has a dedicated setter and read-back getter; {@link #raw} exists
 * only for request parameters the IR does not model yet. Exclusion invariants are checked
 * when the query is translated (not by the setters):
 * <ul>
 *   <li>DSL filter ({@link #filter}/{@link #filterAdd}) and group filter
 *       ({@link #filterGroup}) are mutually exclusive;</li>
 *   <li>{@code limit/offset} and {@code page/hitsPerPage} pagination modes are mutually
 *       exclusive;</li>
 *   <li>{@link #raw} keys must belong to the known request-parameter whitelist and must
 *       not collide with an already-set IR field.</li>
 * </ul>
 * Violations surface as {@link io.github.lamspace.meili.core.exception.MeiliOrmException}
 * with the conflicting names in the message.
 */
public final class MeiliQuery {

    /** Text query; {@code null} means "no text, browse/filter only". */
    private final String q;
    /** Single-string filter DSL, possibly AND-accumulated via {@link #filterAdd}. */
    private String filterDsl;
    /** Grouped filter expressions: AND across groups, OR within a group. */
    private final List<List<String>> filterGroups = new ArrayList<>();
    /** Sort expressions in {@code field:asc|desc} form. */
    private final List<String> sort = new ArrayList<>();
    /** Offset pagination limit; {@code null} when unset. */
    private Integer limit;
    /** Offset pagination skip count; {@code null} when unset. */
    private Integer offset;
    /** Page pagination 1-based page number; {@code null} when unset. */
    private Integer page;
    /** Page pagination size; {@code null} when unset. */
    private Integer hitsPerPage;
    /** Hit attribute allow-list; {@code null} when unset. */
    private List<String> attributesToRetrieve;
    /** Query-match attribute allow-list; {@code null} when unset. */
    private List<String> attributesToSearchOn;
    /** Word matching strategy; {@code null} when unset. */
    private MatchingStrategy matchingStrategy;
    /** Facet request attributes; {@code null} when unset. */
    private List<String> facets;
    /** Match-positions toggle; {@code null} when unstaged. */
    private Boolean showMatchesPosition;
    /** Ranking-score toggle; {@code null} when unstaged. */
    private Boolean showRankingScore;
    /** Distinct dedup attribute; {@code null} when unset. */
    private String distinct;
    /** Semantic-search vector; {@code null} when unset. */
    private List<Double> vector;
    /** Hybrid embedder name; {@code null} when hybrid unstaged. */
    private String hybridEmbedder;
    /** Hybrid semantic ratio; nullable companion of {@link #hybridEmbedder}. */
    private Double hybridSemanticRatio;
    /** Whitelisted extra request parameters keyed by server parameter name. */
    private final Map<String, Object> raw = new LinkedHashMap<>();

    /**
     * Captures the text query; everything else is staged fluently afterwards.
     *
     * @param q text query, possibly {@code null}
     */
    private MeiliQuery(String q) {
        this.q = q;
    }

    /**
     * Starts a query with the given full-text expression.
     *
     * @param q search text; {@code null} or empty browses without text matching
     * @return a new mutable builder
     */
    public static MeiliQuery query(String q) {
        return new MeiliQuery(q);
    }

    /**
     * Sets (replaces) the filter DSL string. Mutually exclusive with {@link #filterGroup}.
     *
     * @param filterDsl server filter expression syntax
     * @return this builder
     */
    public MeiliQuery filter(String filterDsl) {
        this.filterDsl = filterDsl;
        return this;
    }

    /**
     * Appends a filter expression to the DSL, AND-joining with any previous part. Parts
     * containing OR are parenthesized so precedence survives the join.
     *
     * @param filterDsl one filter expression
     * @return this builder
     */
    public MeiliQuery filterAdd(String filterDsl) {
        String part = filterDsl.contains(" OR ") ? "(" + filterDsl + ")" : filterDsl;
        this.filterDsl = this.filterDsl == null || this.filterDsl.isEmpty()
                ? part : this.filterDsl + " AND " + part;
        return this;
    }

    /**
     * Adds one filter group; groups combine with AND while the expressions inside a group
     * combine with OR. Mutually exclusive with the DSL forms.
     *
     * @param orExpressions expressions OR-joined within this group
     * @return this builder
     */
    public MeiliQuery filterGroup(List<String> orExpressions) {
        filterGroups.add(List.copyOf(orExpressions));
        return this;
    }

    /**
     * Sets the sort expressions, replacing any previous list.
     *
     * @param sortExpressions each {@code field:asc} / {@code field:desc}
     * @return this builder
     */
    public MeiliQuery sort(String... sortExpressions) {
        sort.clear();
        sort.addAll(List.of(sortExpressions));
        return this;
    }

    /**
     * Offset pagination: maximum number of hits to return.
     *
     * @param limit hit count
     * @return this builder
     */
    public MeiliQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Offset pagination: number of hits to skip.
     *
     * @param offset rows to skip
     * @return this builder
     */
    public MeiliQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Page pagination: 1-based page number.
     *
     * @param page page ordinal
     * @return this builder
     */
    public MeiliQuery page(int page) {
        this.page = page;
        return this;
    }

    /**
     * Page pagination: hits per page.
     *
     * @param hitsPerPage page size
     * @return this builder
     */
    public MeiliQuery hitsPerPage(int hitsPerPage) {
        this.hitsPerPage = hitsPerPage;
        return this;
    }

    /**
     * Restricts the attributes returned inside hits.
     *
     * @param attributes document field paths to retrieve
     * @return this builder
     */
    public MeiliQuery attributes(String... attributes) {
        this.attributesToRetrieve = List.of(attributes);
        return this;
    }

    /**
     * Restricts which attributes the text query matches against.
     *
     * @param attributes document field paths to search in
     * @return this builder
     */
    public MeiliQuery attributesToSearchOn(String... attributes) {
        this.attributesToSearchOn = List.of(attributes);
        return this;
    }

    /**
     * Sets the word-matching strategy.
     *
     * @param strategy ALL / LAST / FREQUENCY
     * @return this builder
     */
    public MeiliQuery matchingStrategy(MatchingStrategy strategy) {
        this.matchingStrategy = strategy;
        return this;
    }

    /**
     * Requests facet distribution counts for the given attributes.
     *
     * @param facets attribute paths to facet on
     * @return this builder
     */
    public MeiliQuery facets(String... facets) {
        this.facets = List.of(facets);
        return this;
    }

    /**
     * Toggles term match positions in the response.
     *
     * @param show whether to include {@code _matchesPosition}
     * @return this builder
     */
    public MeiliQuery showMatchesPosition(boolean show) {
        this.showMatchesPosition = show;
        return this;
    }

    /**
     * Toggles ranking scores in the response.
     *
     * @param show whether to include {@code _rankingScore}
     * @return this builder
     */
    public MeiliQuery showRankingScore(boolean show) {
        this.showRankingScore = show;
        return this;
    }

    /**
     * Applies attribute-based distinct (deduplication) to the result set.
     *
     * @param attribute field path to dedup on
     * @return this builder
     */
    public MeiliQuery distinct(String attribute) {
        this.distinct = attribute;
        return this;
    }

    /**
     * Supplies a pre-computed vector for semantic search.
     *
     * @param vector embedding components
     * @return this builder
     */
    public MeiliQuery vector(List<Double> vector) {
        this.vector = List.copyOf(vector);
        return this;
    }

    /**
     * Enables hybrid search over the named embedder.
     *
     * @param embedder      embedder configuration name
     * @param semanticRatio optional balance in {@code [0,1]}, may be {@code null}
     * @return this builder
     */
    public MeiliQuery hybrid(String embedder, Double semanticRatio) {
        this.hybridEmbedder = embedder;
        this.hybridSemanticRatio = semanticRatio;
        return this;
    }

    /**
     * Escape hatch: sets any whitelisted request parameter the IR does not model. Rejected
     * at translation if the key is unknown or names a parameter an IR setter already
     * staged.
     *
     * @param key   server parameter name
     * @param value parameter value of the type the parameter expects
     * @return this builder
     */
    public MeiliQuery raw(String key, Object value) {
        raw.put(key, value);
        return this;
    }

    /**
     * Returns the text query.
     *
     * @return q, possibly {@code null}
     */
    public String getQ() {
        return q;
    }

    /**
     * Returns the accumulated DSL filter string.
     *
     * @return filter DSL, or {@code null} when unset
     */
    public String getFilterDsl() {
        return filterDsl;
    }

    /**
     * Returns the staged filter groups.
     *
     * @return immutable list of OR-groups (empty when unset)
     */
    public List<List<String>> getFilterGroups() {
        return List.copyOf(filterGroups);
    }

    /**
     * Returns the sort expressions.
     *
     * @return immutable list, possibly empty
     */
    public List<String> getSort() {
        return List.copyOf(sort);
    }

    /**
     * Returns the offset-mode limit.
     *
     * @return limit or {@code null}
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Returns the offset-mode offset.
     *
     * @return offset or {@code null}
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Returns the page-mode page number.
     *
     * @return page or {@code null}
     */
    public Integer getPage() {
        return page;
    }

    /**
     * Returns the page-mode size.
     *
     * @return hitsPerPage or {@code null}
     */
    public Integer getHitsPerPage() {
        return hitsPerPage;
    }

    /**
     * Returns the hit attribute allow-list.
     *
     * @return attributes or {@code null}
     */
    public List<String> getAttributesToRetrieve() {
        return attributesToRetrieve;
    }

    /**
     * Returns the query-match attribute allow-list.
     *
     * @return attributes or {@code null}
     */
    public List<String> getAttributesToSearchOn() {
        return attributesToSearchOn;
    }

    /**
     * Returns the matching strategy.
     *
     * @return strategy or {@code null}
     */
    public MatchingStrategy getMatchingStrategy() {
        return matchingStrategy;
    }

    /**
     * Returns the facet request attributes.
     *
     * @return facets or {@code null}
     */
    public List<String> getFacets() {
        return facets;
    }

    /**
     * Returns the match-positions toggle.
     *
     * @return flag or {@code null} when unstaged
     */
    public Boolean getShowMatchesPosition() {
        return showMatchesPosition;
    }

    /**
     * Returns the ranking-score toggle.
     *
     * @return flag or {@code null} when unstaged
     */
    public Boolean getShowRankingScore() {
        return showRankingScore;
    }

    /**
     * Returns the distinct dedup attribute.
     *
     * @return attribute or {@code null}
     */
    public String getDistinct() {
        return distinct;
    }

    /**
     * Returns the semantic-search vector.
     *
     * @return vector or {@code null}
     */
    public List<Double> getVector() {
        return vector;
    }

    /**
     * Returns the hybrid embedder name.
     *
     * @return embedder or {@code null} when hybrid unstaged
     */
    public String getHybridEmbedder() {
        return hybridEmbedder;
    }

    /**
     * Returns the hybrid semantic ratio.
     *
     * @return ratio or {@code null}
     */
    public Double getHybridSemanticRatio() {
        return hybridSemanticRatio;
    }

    /**
     * Returns the escape-hatch parameters.
     *
     * @return immutable copy of raw entries keyed by server parameter name
     */
    public Map<String, Object> getRaw() {
        return Map.copyOf(raw);
    }
}
