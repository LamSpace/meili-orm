package io.github.lamspace.meili.core.mapping;

/**
 * An immutable leaf property of an entity: its dotted document path and the settings
 * roles declared on it. Values are derived once by {@link MeiliPersistentEntity#of(Class)}
 * and never mutated afterwards.
 *
 * <p>A leaf is either a simple-typed field, or an aggregate field the flattening walk
 * stopped at (cycle guard, depth cap, or collection element opacity); role flags may be
 * declared on either, but never on a field that itself expands into children.
 */
public final class MeiliPersistentProperty {

    /** Dotted document path, e.g. {@code author.city} or {@code book_title}. */
    private final String jsonPath;
    /** Whether this property is the entity primary key. */
    private final boolean id;
    /** Projected into {@code searchableAttributes}. */
    private final boolean searchable;
    /** Explicit search weight; {@code -1} when unordered. */
    private final int searchableOrder;
    /** Projected into {@code filterableAttributes}. */
    private final boolean filterable;
    /** Projected into {@code sortableAttributes}. */
    private final boolean sortable;
    /** Projected into {@code displayedAttributes}. */
    private final boolean displayed;

    /**
     * Creates a frozen property view. Called only from entity parsing.
     *
     * @param jsonPath       dotted document path
     * @param id             primary-key marker
     * @param searchable     searchable role flag
     * @param searchableOrder explicit search weight or {@code -1}
     * @param filterable     filterable role flag
     * @param sortable       sortable role flag
     * @param displayed      displayed role flag
     */
    MeiliPersistentProperty(String jsonPath, boolean id, boolean searchable,
                            int searchableOrder, boolean filterable, boolean sortable, boolean displayed) {
        this.jsonPath = jsonPath;
        this.id = id;
        this.searchable = searchable;
        this.searchableOrder = searchableOrder;
        this.filterable = filterable;
        this.sortable = sortable;
        this.displayed = displayed;
    }

    /**
     * Returns the dotted document path of this property.
     *
     * @return document path such as {@code price} or {@code author.city}
     */
    public String getJsonPath() {
        return jsonPath;
    }

    /**
     * Returns whether this property is the primary key.
     *
     * @return {@code true} for the single {@code @MeiliId} property
     */
    public boolean isId() {
        return id;
    }

    /**
     * Returns the searchable role flag.
     *
     * @return {@code true} if projected into {@code searchableAttributes}
     */
    public boolean isSearchable() {
        return searchable;
    }

    /**
     * Returns the explicit search weight.
     *
     * @return the declared order, or {@code -1} when unordered
     */
    public int getSearchableOrder() {
        return searchableOrder;
    }

    /**
     * Returns the filterable role flag.
     *
     * @return {@code true} if projected into {@code filterableAttributes}
     */
    public boolean isFilterable() {
        return filterable;
    }

    /**
     * Returns the sortable role flag.
     *
     * @return {@code true} if projected into {@code sortableAttributes}
     */
    public boolean isSortable() {
        return sortable;
    }

    /**
     * Returns the displayed role flag.
     *
     * @return {@code true} if projected into {@code displayedAttributes}
     */
    public boolean isDisplayed() {
        return displayed;
    }

    @Override
    public String toString() {
        return "MeiliPersistentProperty[" + jsonPath + (id ? " id" : "") + "]";
    }
}
