package io.github.lamspace.meili.core.exception;

/**
 * Raised when entity metadata violates the mapping contract: a missing or duplicated
 * primary-key declaration, an unsupported primary-key type, a projection-name conflict,
 * an illegal role annotation on a container field, an audit field outside its allowed
 * type set, or a broken settings passthrough.
 *
 * <p>This exception is a <em>startup fail-fast</em> signal: it is thrown while the
 * metamodel or the settings projection is being built, before any server request is
 * issued, and it always names the offending entity and member in its message so the
 * declaration can be fixed without further diagnosis.
 *
 * <p>Immutable and thread-safe.
 */
public final class MeiliMappingException extends MeiliOrmException {

    /** Fixed serialization version identifier. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a mapping failure with a detail message.
     *
     * @param message description naming the offending entity/member or resource
     */
    public MeiliMappingException(String message) {
        super(message);
    }

    /**
     * Creates a mapping failure wrapping an underlying cause (e.g. an IO error while
     * reading a passthrough settings resource).
     *
     * @param message description naming the offending entity/member or resource
     * @param cause the underlying failure being translated
     */
    public MeiliMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
