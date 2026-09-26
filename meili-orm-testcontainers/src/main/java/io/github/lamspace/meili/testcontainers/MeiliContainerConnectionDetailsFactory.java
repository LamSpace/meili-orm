package io.github.lamspace.meili.testcontainers;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/**
 * {@link ContainerConnectionDetailsFactory} contributing {@link MeiliConnectionDetails} for a
 * {@link MeiliSearchContainer} annotated with {@code @ServiceConnection}.
 *
 * <p>Registered through {@code META-INF/spring.factories} under the Boot service-connection
 * factory SPI ({@code org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory});
 * it is discovered by both Boot generations this module targets. Users never instantiate it —
 * declare a static container field with {@code @ServiceConnection} and the test context calls
 * it when building the connection-details bean.
 *
 * <p>Contribution scope: exactly one bean, the {@link MeiliConnectionDetails} supply of URL and
 * API key. The SDK client and data-layer beans stay owned by the regular auto-configuration,
 * which already backs off its property-backed default in favour of any
 * {@link MeiliConnectionDetails} bean (including a user-declared one, per that contract).
 *
 * <p>Thread model and lifecycle: inherited from the Boot mechanism — the details bean is
 * created once during context startup, the container start happens on first connection-fact
 * access, and every later read returns the same stable values.
 */
public class MeiliContainerConnectionDetailsFactory
        extends ContainerConnectionDetailsFactory<MeiliSearchContainer, MeiliConnectionDetails> {

    /**
     * Instantiated reflectively by the service-connection discovery mechanism.
     */
    public MeiliContainerConnectionDetailsFactory() {
    }

    @Override
    protected MeiliConnectionDetails getContainerConnectionDetails(
            ContainerConnectionSource<MeiliSearchContainer> source) {
        return new MeiliContainerConnectionDetails(source);
    }

    /**
     * {@link MeiliConnectionDetails} backed by a {@link ContainerConnectionSource}'s container.
     *
     * <p>Reads delegate to the live container, which Boot's base class starts on first access;
     * values are stable afterwards, matching the {@link MeiliConnectionDetails} contract.
     */
    private static final class MeiliContainerConnectionDetails
            extends ContainerConnectionDetailsFactory.ContainerConnectionDetails<MeiliSearchContainer>
            implements MeiliConnectionDetails {

        /**
         * Binds this supply to the declared container source.
         *
         * @param source the container connection source owned by the service-connection mechanism
         */
        private MeiliContainerConnectionDetails(ContainerConnectionSource<MeiliSearchContainer> source) {
            super(source);
        }

        @Override
        public String getUrl() {
            return getContainer().getUrl();
        }

        @Override
        public String getApiKey() {
            return getContainer().getApiKey();
        }
    }
}
