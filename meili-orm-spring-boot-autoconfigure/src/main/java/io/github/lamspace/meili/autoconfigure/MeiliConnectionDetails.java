/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
