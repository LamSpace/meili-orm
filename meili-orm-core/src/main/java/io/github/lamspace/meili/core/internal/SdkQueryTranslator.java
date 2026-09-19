package io.github.lamspace.meili.core.internal;

import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Hybrid;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.query.MeiliQuery;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates the transport-free {@link MeiliQuery} IR into the official client's request
 * object, enforcing the IR's exclusion invariants at the boundary
 * (DSL-vs-group filters, the two pagination modes, raw-key whitelist and raw-vs-explicit
 * collisions).
 *
 * <p>Every rejected combination throws {@link MeiliOrmException} naming the conflicting
 * parameters; nothing is sent to the server when translation fails. Translation is a pure
 * function; thread-safe.
 *
 * <p>Internal SPI — the official client type in the return signature is contained
 * here and never reaches the public core API.
 */
public final class SdkQueryTranslator {

    /**
     * Utility holder.
     */
    private SdkQueryTranslator() {
    }

    /**
     * Builds the client request object for one staged IR query.
     *
     * @param query fully staged IR query
     * @return the equivalent request object
     * @throws MeiliOrmException on any violated exclusion invariant or bad raw entry
     */
    public static SearchRequest toSearchRequest(MeiliQuery query) {
        SearchRequest req = new SearchRequest(query.getQ());
        Map<String, Object> raw = query.getRaw();
        Set<String> staged = stagedKeys(query);

        mapFilter(req, query);
        mapPagination(req, query);

        if (!query.getSort().isEmpty()) {
            req.setSort(query.getSort().toArray(String[]::new));
        }
        if (query.getAttributesToRetrieve() != null) {
            req.setAttributesToRetrieve(toArray(query.getAttributesToRetrieve()));
        }
        if (query.getAttributesToSearchOn() != null) {
            req.setAttributesToSearchOn(toArray(query.getAttributesToSearchOn()));
        }
        if (query.getFacets() != null) {
            req.setFacets(toArray(query.getFacets()));
        }
        if (query.getMatchingStrategy() != null) {
            req.setMatchingStrategy(com.meilisearch.sdk.model.MatchingStrategy
                    .valueOf(query.getMatchingStrategy().name()));
        }
        if (query.getShowMatchesPosition() != null) {
            req.setShowMatchesPosition(query.getShowMatchesPosition());
        }
        if (query.getShowRankingScore() != null) {
            req.setShowRankingScore(query.getShowRankingScore());
        }
        if (query.getDistinct() != null) {
            req.setDistinct(query.getDistinct());
        }
        if (query.getVector() != null) {
            req.setVector(query.getVector().toArray(Double[]::new));
        }
        if (query.getHybridEmbedder() != null || query.getHybridSemanticRatio() != null) {
            req.setHybrid(Hybrid.builder()
                    .embedder(query.getHybridEmbedder())
                    .semanticRatio(query.getHybridSemanticRatio())
                    .build());
        }

        applyRaw(req, raw, staged);
        return req;
    }

    /**
     * Rejects DSL/group mixing and maps whichever form is present.
     *
     * @param req   target request
     * @param query IR source
     */
    private static void mapFilter(SearchRequest req, MeiliQuery query) {
        boolean hasDsl = query.getFilterDsl() != null;
        boolean hasGroups = !query.getFilterGroups().isEmpty();
        if (hasDsl && hasGroups) {
            throw new MeiliOrmException("filter 互斥：filter/filterAdd DSL 与 filterGroup 分组不可混用");
        }
        if (hasDsl) {
            req.setFilter(new String[]{query.getFilterDsl()});
        }
        if (hasGroups) {
            req.setFilterArray(query.getFilterGroups().stream()
                    .map(SdkQueryTranslator::toArray).toArray(String[][]::new));
        }
    }

    /**
     * Rejects pagination-mode mixing and copies whichever mode is present.
     *
     * @param req   target request
     * @param query IR source
     */
    private static void mapPagination(SearchRequest req, MeiliQuery query) {
        boolean offsetMode = query.getLimit() != null || query.getOffset() != null;
        boolean pageMode = query.getPage() != null || query.getHitsPerPage() != null;
        if (offsetMode && pageMode) {
            throw new MeiliOrmException("分页模式互斥：limit/offset 与 page/hitsPerPage 不可混设");
        }
        if (query.getLimit() != null) {
            req.setLimit(query.getLimit());
        }
        if (query.getOffset() != null) {
            req.setOffset(query.getOffset());
        }
        if (query.getPage() != null) {
            req.setPage(query.getPage());
        }
        if (query.getHitsPerPage() != null) {
            req.setHitsPerPage(query.getHitsPerPage());
        }
    }

    /**
     * Computes which request parameters an IR setter already staged, for raw-collision
     * detection.
     *
     * @param query IR source
     * @return parameter names occupied by explicit IR fields
     */
    private static Set<String> stagedKeys(MeiliQuery query) {
        Set<String> staged = new HashSet<>();
        if (query.getQ() != null) {
            staged.add("q");
        }
        if (query.getFilterDsl() != null) {
            staged.add("filter");
        }
        if (!query.getFilterGroups().isEmpty()) {
            staged.add("filterArray");
        }
        if (!query.getSort().isEmpty()) {
            staged.add("sort");
        }
        putIfSet(staged, query.getLimit() != null, "limit");
        putIfSet(staged, query.getOffset() != null, "offset");
        putIfSet(staged, query.getPage() != null, "page");
        putIfSet(staged, query.getHitsPerPage() != null, "hitsPerPage");
        putIfSet(staged, query.getAttributesToRetrieve() != null, "attributesToRetrieve");
        putIfSet(staged, query.getAttributesToSearchOn() != null, "attributesToSearchOn");
        putIfSet(staged, query.getFacets() != null, "facets");
        putIfSet(staged, query.getMatchingStrategy() != null, "matchingStrategy");
        putIfSet(staged, query.getShowMatchesPosition() != null, "showMatchesPosition");
        putIfSet(staged, query.getShowRankingScore() != null, "showRankingScore");
        putIfSet(staged, query.getDistinct() != null, "distinct");
        putIfSet(staged, query.getVector() != null, "vector");
        putIfSet(staged, query.getHybridEmbedder() != null
                || query.getHybridSemanticRatio() != null, "hybrid");
        return staged;
    }

    /**
     * Records one occupied parameter name when set.
     *
     * @param staged accumulator
     * @param isSet  whether the IR field was staged
     * @param key    parameter name
     */
    private static void putIfSet(Set<String> staged, boolean isSet, String key) {
        if (isSet) {
            staged.add(key);
        }
    }

    /**
     * Applies whitelist-checked escape-hatch parameters last, rejecting unknown keys,
     * collisions with explicit IR fields, and mistyped values.
     *
     * @param req    target request
     * @param raw    staged raw entries
     * @param staged parameter names already occupied by IR setters
     */
    private static void applyRaw(SearchRequest req, Map<String, Object> raw, Set<String> staged) {
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (staged.contains(key)) {
                throw new MeiliOrmException("raw() 键 " + key + " 与显式设置的 IR 字段冲突");
            }
            switch (key) {
                case "q" -> req.setQ(asString(key, value));
                case "distinct" -> req.setDistinct(asString(key, value));
                case "cropMarker" -> req.setCropMarker(asString(key, value));
                case "highlightPreTag" -> req.setHighlightPreTag(asString(key, value));
                case "highlightPostTag" -> req.setHighlightPostTag(asString(key, value));
                case "matchingStrategy" -> req.setMatchingStrategy(
                        com.meilisearch.sdk.model.MatchingStrategy.valueOf(asString(key, value)));
                case "filter" -> req.setFilter(value instanceof String s
                        ? new String[]{s} : asStringList(key, value).toArray(String[]::new));
                case "filterArray" -> req.setFilterArray(asFilterArray(key, value));
                case "sort" -> req.setSort(asStringList(key, value).toArray(String[]::new));
                case "attributesToRetrieve" ->
                        req.setAttributesToRetrieve(asStringList(key, value).toArray(String[]::new));
                case "attributesToSearchOn" ->
                        req.setAttributesToSearchOn(asStringList(key, value).toArray(String[]::new));
                case "attributesToCrop" ->
                        req.setAttributesToCrop(asStringList(key, value).toArray(String[]::new));
                case "attributesToHighlight" ->
                        req.setAttributesToHighlight(asStringList(key, value).toArray(String[]::new));
                case "facets" -> req.setFacets(asStringList(key, value).toArray(String[]::new));
                case "locales" -> req.setLocales(asStringList(key, value).toArray(String[]::new));
                case "limit" -> req.setLimit(asInteger(key, value));
                case "offset" -> req.setOffset(asInteger(key, value));
                case "page" -> req.setPage(asInteger(key, value));
                case "hitsPerPage" -> req.setHitsPerPage(asInteger(key, value));
                case "cropLength" -> req.setCropLength(asInteger(key, value));
                case "showMatchesPosition" -> req.setShowMatchesPosition(asBoolean(key, value));
                case "showRankingScore" -> req.setShowRankingScore(asBoolean(key, value));
                case "showRankingScoreDetails" ->
                        req.setShowRankingScoreDetails(asBoolean(key, value));
                case "retrieveVectors" -> req.setRetrieveVectors(asBoolean(key, value));
                case "rankingScoreThreshold" -> req.setRankingScoreThreshold(asDouble(key, value));
                case "vector" -> req.setVector(asVector(key, value));
                case "hybrid" -> req.setHybrid(asHybrid(key, value));
                default -> throw new MeiliOrmException("raw() 未知键: " + key);
            }
        }
    }

    /**
     * Casts to String with a typed failure.
     *
     * @param key   parameter name (for the message)
     * @param value staged value
     * @return the string value
     */
    private static String asString(String key, Object value) {
        if (value instanceof String s) {
            return s;
        }
        throw badType(key, value);
    }

    /**
     * Casts to a list of strings with a typed failure.
     *
     * @param key   parameter name
     * @param value staged value
     * @return the list value
     */
    private static List<String> asStringList(String key, Object value) {
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof String)) {
                    throw badType(key, value);
                }
            }
            @SuppressWarnings("unchecked")
            List<String> typed = (List<String>) list;
            return typed;
        }
        throw badType(key, value);
    }

    /**
     * Casts to an integer value accepting any {@link Number}.
     *
     * @param key   parameter name
     * @param value staged value
     * @return the int value
     */
    private static Integer asInteger(String key, Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw badType(key, value);
    }

    /**
     * Casts to a boolean flag.
     *
     * @param key   parameter name
     * @param value staged value
     * @return the boolean value
     */
    private static Boolean asBoolean(String key, Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw badType(key, value);
    }

    /**
     * Casts to a double value accepting any {@link Number}.
     *
     * @param key   parameter name
     * @param value staged value
     * @return the double value
     */
    private static Double asDouble(String key, Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw badType(key, value);
    }

    /**
     * Casts to a vector of doubles.
     *
     * @param key   parameter name
     * @param value staged value
     * @return the vector array
     */
    private static Double[] asVector(String key, Object value) {
        if (value instanceof List<?> list && list.stream().allMatch(o -> o instanceof Double)) {
            return list.stream().map(o -> (Double) o).toArray(Double[]::new);
        }
        throw badType(key, value);
    }

    /**
     * Casts a nested list-of-lists into the group array form.
     *
     * @param key   parameter name
     * @param value staged value
     * @return filter groups
     */
    private static String[][] asFilterArray(String key, Object value) {
        if (value instanceof List<?> outer) {
            return outer.stream().map(g -> asStringList(key, g).toArray(String[]::new))
                    .toArray(String[][]::new);
        }
        throw badType(key, value);
    }

    /**
     * Builds a hybrid descriptor from its map form ({@code embedder} required,
     * {@code semanticRatio} optional).
     *
     * @param key   parameter name
     * @param value staged value
     * @return hybrid configuration
     */
    private static Hybrid asHybrid(String key, Object value) {
        if (value instanceof Map<?, ?> m && m.get("embedder") instanceof String embedder) {
            Double ratio = m.get("semanticRatio") instanceof Number n ? n.doubleValue() : null;
            return Hybrid.builder().embedder(embedder).semanticRatio(ratio).build();
        }
        throw badType(key, value);
    }

    /**
     * Produces the uniform bad-type failure for raw entries.
     *
     * @param key   parameter name
     * @param value rejected value
     * @return the exception to throw
     */
    private static MeiliOrmException badType(String key, Object value) {
        return new MeiliOrmException("raw() 键 " + key + " 的值类型不支持: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    /**
     * Copies a string list into a fresh array.
     *
     * @param list source list
     * @return array copy
     */
    private static String[] toArray(List<String> list) {
        return list.toArray(String[]::new);
    }
}
