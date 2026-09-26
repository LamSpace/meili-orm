package io.github.lamspace.meili.repository.exception;

/**
 * Repository bootstrap configuration error: a method's grammar, argument shape or return
 * type violates the supported surface. Extends {@link IllegalArgumentException} so callers
 * can treat every startup-time repository failure uniformly (the derived-query capability
 * contract specifies this exception family for unsupported shapes; mapping-level role
 * failures use the core {@code MeiliMappingException}).
 */
public class MeiliRepositoryConfigurationException extends IllegalArgumentException {

    /**
     * Creates the exception with a locating message.
     *
     * @param message locating detail (always includes the offending method name)
     */
    public MeiliRepositoryConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message locating detail
     * @param cause   underlying failure
     */
    public MeiliRepositoryConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
