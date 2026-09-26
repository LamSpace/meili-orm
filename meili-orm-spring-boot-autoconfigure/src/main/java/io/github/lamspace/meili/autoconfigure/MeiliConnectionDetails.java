package io.github.lamspace.meili.autoconfigure;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

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
 * <p>The interface extends Boot's {@link ConnectionDetails} marker (and adds no methods of its
 * own) so that beans of this type participate in the Boot service-connection machinery — a
 * {@code @ServiceConnection} bridge contributes exactly such a bean. The marker changes nothing
 * about the back-off contract above, and Boot's own property details back off per sub-interface,
 * never on the marker type, so a bean here cannot retire unrelated services' defaults.
 *
 * <p>Implementations MUST NOT block on network validation here — reachability is a concern of
 * the client itself and, at startup, of index initialization.
 */
public interface MeiliConnectionDetails extends ConnectionDetails {

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
