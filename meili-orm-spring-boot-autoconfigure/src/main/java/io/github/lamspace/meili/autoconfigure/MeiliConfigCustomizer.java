package io.github.lamspace.meili.autoconfigure;

import com.meilisearch.sdk.Config;

/**
 * Callback for customizing the SDK {@link Config} before the client is constructed from it.
 *
 * <p>Contract tier: extension SPI. All beans of this type are applied in ordered-stream order
 * (honor {@code @Order}/{@link org.springframework.core.Ordered} when relative sequencing
 * matters), each exactly once, against the same {@link Config} instance that the client bean is
 * subsequently built with. The config is passed mutable on purpose — headers, user agents and
 * the JSON handler can be adjusted here.
 *
 * <p>Callers' obligation: do not retain the {@link Config} beyond the callback; do not assume
 * the default JSON handler survives — replacing it is possible but unsupported for entity
 * read/write, which flows through raw-string APIs by design.
 */
@FunctionalInterface
public interface MeiliConfigCustomizer {

    /**
     * Customizes the configuration in place.
     *
     * @param config the config that will build the client; never {@code null}
     */
    void customize(Config config);
}
