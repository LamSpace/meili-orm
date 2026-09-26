package io.github.lamspace.meili.autoconfigure;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Client-layer auto configuration: turns {@code meili.*} (or a user-supplied
 * {@link MeiliConnectionDetails}) into the SDK {@link Config} and {@link Client} beans that the
 * data layer builds on.
 *
 * <p>Assembly chain (each link backs off on a user bean of the same type):
 * {@code MeiliConnectionDetails -> Config (+ MeiliConfigCustomizer SPI) -> Client}.
 *
 * <p>Activation: requires {@code com.meilisearch.sdk.Client} on the classpath and
 * {@code meili.enabled} absent or {@code true}. When {@code meili.enabled=false}, nothing in
 * this class — nor anything downstream — is created.
 *
 * <p>Replacement contract: {@link Config} is exposed as a bean because the core gateway reads
 * the same connection facts the client was built with. Replacing only {@link Client} with a
 * user bean is supported but then the auto-configured {@link Config} stays property-derived;
 * point a client elsewhere and also supply a matching {@link Config} (or your own gateway) so
 * raw-HTTP calls and the client agree on URL and key.
 *
 * <p>The configured client keeps the SDK default JSON handler: every entity read/write flows
 * through the raw-string APIs of the core gateway, and the SDK typed convenience APIs rely on
 * adapters registered by that default handler.
 */
@AutoConfiguration
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(prefix = "meili", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MeiliProperties.class)
public class MeiliClientAutoConfiguration {

    /**
     * Instantiated by the auto-configuration machinery; all behavior lives in the bean methods.
     */
    public MeiliClientAutoConfiguration() {
    }

    /**
     * Default connection details sourced from {@code meili.url}/{@code meili.api-key}.
     *
     * @param properties the bound properties
     * @return the properties-backed details
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliConnectionDetails meiliConnectionDetails(MeiliProperties properties) {
        return new PropertiesMeiliConnectionDetails(properties);
    }

    /**
     * Builds the SDK configuration and applies every {@link MeiliConfigCustomizer} to it.
     *
     * <p>The {@code meili.client-agents} tokens are handed to the SDK {@link Config}
     * constructor: the User-Agent header is computed during construction and the headers map is
     * final afterwards, so construction is the only injection point. The SDK prefixes its own
     * version token, so the starter passes just its identity tokens. Construction keeps the
     * default JSON handler.
     *
     * @param details     connection facts (user bean wins over the properties default)
     * @param properties  bound {@code meili.*} values, supplying the client agents
     * @param customizers ordered stream of customization callbacks, possibly empty
     * @return the config the client and the core gateway are built from
     */
    @Bean
    @ConditionalOnMissingBean
    Config meiliConfig(MeiliConnectionDetails details, MeiliProperties properties,
                       ObjectProvider<MeiliConfigCustomizer> customizers) {
        Config config = new Config(details.getUrl(), details.getApiKey(),
                properties.getClientAgents().toArray(new String[0]));
        customizers.orderedStream().forEach(customizer -> customizer.customize(config));
        return config;
    }

    /**
     * The shared SDK client. Construction performs no network I/O — unreachability surfaces at
     * first use or during startup index initialization.
     *
     * @param config the auto-configured (or user-supplied) SDK configuration
     * @return the client bean
     */
    @Bean
    @ConditionalOnMissingBean
    Client meiliClient(Config config) {
        return new Client(config);
    }
}
