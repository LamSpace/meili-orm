package io.github.lamspace.meili.core.exception;

/**
 * Raised when an index operation fails on the server or in transit: API errors
 * (index not found, invalid filter, bad settings payload), transport failures and
 * unexpected response shapes alike.
 *
 * <p>Both the official-client channel and the gateway's own HTTP channel funnel every
 * failure into this single type, so {@link #getMeiliCode()} is the only server error
 * detail callers should rely on; it is {@code null} when the failure has no server-side
 * error code (connection dropped, malformed body).
 *
 * <p>Immutable and thread-safe.
 */
public final class MeiliIndexAccessException extends MeiliOrmException {

    /** Fixed serialization version identifier. */
    private static final long serialVersionUID = 1L;

    /** MeiliSearch API error code, or {@code null} if the failure carried none. */
    private final String meiliCode;

    /**
     * Creates an access failure.
     *
     * @param meiliCode server-side error code (e.g. {@code index_not_found}); {@code null}
     *                  when the failure originates below the HTTP layer
     * @param message   human-readable failure description
     * @param cause     the underlying client or transport failure being translated
     */
    public MeiliIndexAccessException(String meiliCode, String message, Throwable cause) {
        super(message, cause);
        this.meiliCode = meiliCode;
    }

    /**
     * Creates an access failure without a transport cause (e.g. a locally detected
     * protocol violation).
     *
     * @param meiliCode server-side error code, or {@code null} if none applies
     * @param message   human-readable failure description
     */
    public MeiliIndexAccessException(String meiliCode, String message) {
        super(message);
        this.meiliCode = meiliCode;
    }

    /**
     * Returns the MeiliSearch error code carried by the failed call.
     *
     * @return the server error code, or {@code null} if the failure had none
     */
    public String getMeiliCode() {
        return meiliCode;
    }
}
