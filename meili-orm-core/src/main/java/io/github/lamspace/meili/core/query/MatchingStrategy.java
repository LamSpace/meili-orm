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
