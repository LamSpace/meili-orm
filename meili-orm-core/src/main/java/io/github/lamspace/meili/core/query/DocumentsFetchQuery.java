package io.github.lamspace.meili.core.query;

import java.util.ArrayList;
import java.util.List;

/**
 * IR for "fetch stored documents by criteria" (the server-side documents-fetch semantics:
 * filter, field selection, sort, pagination — no text matching). Builder-shaped like
 * {@link MeiliQuery}: mutable, single-thread, fully staged before use.
 */
public final class DocumentsFetchQuery {

    /** Filter DSL string, AND-accumulated. */
    private String filterDsl;
    /** Sort expressions {@code field:asc|desc}. */
    private final List<String> sort = new ArrayList<>();
    /** Returned field paths; empty means server default (all). */
    private final List<String> fields = new ArrayList<>();
    /** Row skip count; {@code null} when unset. */
    private Integer offset;
    /** Row cap; {@code null} when unset. */
    private Integer limit;

    /** Starts from an unstaged state; use {@link #fetchQuery()}. */
    private DocumentsFetchQuery() {
    }

    /**
     * Starts a fetch query.
     *
     * @return a new mutable builder
     */
    public static DocumentsFetchQuery fetchQuery() {
        return new DocumentsFetchQuery();
    }

    /**
     * Sets (replaces) the filter DSL.
     *
     * @param filterDsl server filter expression
     * @return this builder
     */
    public DocumentsFetchQuery filter(String filterDsl) {
        this.filterDsl = filterDsl;
        return this;
    }

    /**
     * Appends a filter expression with AND (OR-containing parts get parenthesized).
     *
     * @param filterDsl one expression
     * @return this builder
     */
    public DocumentsFetchQuery filterAdd(String filterDsl) {
        String part = filterDsl.contains(" OR ") ? "(" + filterDsl + ")" : filterDsl;
        this.filterDsl = this.filterDsl == null || this.filterDsl.isEmpty()
                ? part : this.filterDsl + " AND " + part;
        return this;
    }

    /**
     * Sets sort expressions, replacing previous ones.
     *
     * @param sortExpressions {@code field:asc} / {@code field:desc}
     * @return this builder
     */
    public DocumentsFetchQuery sort(String... sortExpressions) {
        sort.clear();
        sort.addAll(List.of(sortExpressions));
        return this;
    }

    /**
     * Restricts returned fields.
     *
     * @param fieldPaths document field paths
     * @return this builder
     */
    public DocumentsFetchQuery fields(String... fieldPaths) {
        fields.clear();
        fields.addAll(List.of(fieldPaths));
        return this;
    }

    /**
     * Sets the row skip count.
     *
     * @param offset rows to skip
     * @return this builder
     */
    public DocumentsFetchQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Sets the row cap.
     *
     * @param limit maximum rows
     * @return this builder
     */
    public DocumentsFetchQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Returns the filter DSL.
     *
     * @return filter or {@code null}
     */
    public String getFilterDsl() {
        return filterDsl;
    }

    /**
     * Returns sort expressions.
     *
     * @return immutable list, possibly empty
     */
    public List<String> getSort() {
        return List.copyOf(sort);
    }

    /**
     * Returns the field allow-list.
     *
     * @return immutable list, empty for "all fields"
     */
    public List<String> getFields() {
        return List.copyOf(fields);
    }

    /**
     * Returns the row skip count.
     *
     * @return offset or {@code null}
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Returns the row cap.
     *
     * @return limit or {@code null}
     */
    public Integer getLimit() {
        return limit;
    }
}
