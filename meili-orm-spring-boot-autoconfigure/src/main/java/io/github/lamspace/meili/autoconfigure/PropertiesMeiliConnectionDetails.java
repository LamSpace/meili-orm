package io.github.lamspace.meili.autoconfigure;

/**
 * {@link MeiliConnectionDetails} fed straight from {@link MeiliProperties}
 * ({@code meili.url} / {@code meili.api-key}).
 *
 * <p>This is the auto-configured default; it backs off as soon as any other
 * {@link MeiliConnectionDetails} bean is present. Immutable after construction; the bound
 * properties are read at call time, so late property mutation would surface here, which is
 * not a supported configuration path anyway.
 */
class PropertiesMeiliConnectionDetails implements MeiliConnectionDetails {

    /** The properties being mirrored. */
    private final MeiliProperties properties;

    /**
     * Wraps the bound properties.
     *
     * @param properties bound {@code meili.*} values to expose; not retained beyond reference
     */
    PropertiesMeiliConnectionDetails(MeiliProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getUrl() {
        return properties.getUrl();
    }

    @Override
    public String getApiKey() {
        return properties.getApiKey();
    }
}
