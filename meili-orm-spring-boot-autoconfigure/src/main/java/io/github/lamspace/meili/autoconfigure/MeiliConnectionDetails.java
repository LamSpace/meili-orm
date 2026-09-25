package io.github.lamspace.meili.autoconfigure;

/**
 * Supply of the connection facts needed to build the MeiliSearch client: the service URL and
 * the API key.
 *
 * <p>Contract tier: seam interface. It exists so the source of connection information is
 * replaceable without touching property configuration — declaring a bean of this type retires
 * the properties-backed default entirely (the default backs off on any user-supplied instance).
 * Implementations are consulted once during client construction; they must be thread-safe and
 * must keep returning stable values afterwards.
 *
 * <p>Implementations MUST NOT block on network validation here — reachability is a concern of
 * the client itself and, at startup, of index initialization.
 */
public interface MeiliConnectionDetails {

    /**
     * The MeiliSearch service base URL, including scheme (no trailing slash required).
     *
     * @return the service URL; never {@code null}
     */
    String getUrl();

    /**
     * The API key used for authentication (master key or a derived key).
     *
     * @return the API key; may be empty for an unauthenticated server, never {@code null}
     */
    String getApiKey();
}
