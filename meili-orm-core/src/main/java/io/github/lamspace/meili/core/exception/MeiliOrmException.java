package io.github.lamspace.meili.core.exception;

/**
 * Unchecked root of the whole meili-orm core exception hierarchy.
 *
 * <p>Every failure surfaced by core — mapping validation, document serialization,
 * server access, task waiting — is normalized into a subclass of this exception so
 * that callers can intercept the entire library with a single {@code catch} and never
 * depend on transport-layer exception types.
 *
 * <p>Instances are thrown, never caught-and-stored; they carry no mutable state and
 * are therefore trivially thread-safe.
 */
public class MeiliOrmException extends RuntimeException {

    /** Fixed serialization version identifier. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a root exception with a detail message.
     *
     * @param message human-readable failure description
     */
    public MeiliOrmException(String message) {
        super(message);
    }

    /**
     * Creates a root exception wrapping a lower-level cause.
     *
     * @param message human-readable failure description
     * @param cause the underlying failure being translated
     */
    public MeiliOrmException(String message, Throwable cause) {
        super(message, cause);
    }
}
