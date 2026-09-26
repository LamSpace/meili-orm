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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.core.event.MeiliCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.internal.SdkMeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.DefaultMeiliSearchOperations;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Data-layer auto configuration: builds the operations template and its collaborators on top
 * of an available SDK {@link Client}.
 *
 * <p>Assembly (each link backs off on a user bean of the same type):
 * {@code MeiliDocumentSerializer -> MeiliMappingContext -> MeiliEntityCallbacks ->
 * MeiliRawGateway -> MeiliSearchOperations}. The serializer prefers the container's
 * {@link ObjectMapper} when one exists (the common Boot 3 shape); with none present it builds
 * a private instance with the stable defaults the core serializer documents — behavior stays
 * correct either way, and a user {@link MeiliDocumentSerializer} bean replaces the whole
 * strategy.
 *
 * <p>Activation: requires a {@link Client} bean (auto-configured or user-supplied) and
 * {@code meili.enabled} absent or {@code true}. Callback collection takes every
 * {@link MeiliCallback} bean in the context; lifecycle participation is decided by the core
 * registry from each callback's declared generic argument.
 */
@AutoConfiguration(after = MeiliClientAutoConfiguration.class)
@ConditionalOnBean(Client.class)
@ConditionalOnProperty(prefix = "meili", name = "enabled", matchIfMissing = true)
public class MeiliDataAutoConfiguration {

    /**
     * Instantiated by the auto-configuration machinery; all behavior lives in the bean methods.
     */
    public MeiliDataAutoConfiguration() {
    }

    /**
     * The document serializer: container {@link ObjectMapper} when available, otherwise a
     * private mapper with the core serializer's stable defaults.
     *
     * @param objectMappers provider for an optional container ObjectMapper
     * @return the serializer used for every entity read/write
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliDocumentSerializer meiliDocumentSerializer(ObjectProvider<ObjectMapper> objectMappers) {
        return new Jackson2DocumentSerializer(objectMappers.getIfAvailable(ObjectMapper::new));
    }

    /**
     * The entity metamodel cache.
     *
     * @return a shared, lazily-populated mapping context
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliMappingContext meiliMappingContext() {
        return new MeiliMappingContext();
    }

    /**
     * Collects every {@link MeiliCallback} bean into the lifecycle registry used by all
     * operations.
     *
     * @param callbacks provider stream over callback beans, possibly empty
     * @return the populated registry
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliEntityCallbacks meiliEntityCallbacks(ObjectProvider<MeiliCallback> callbacks) {
        MeiliEntityCallbacks registry = new MeiliEntityCallbacks();
        callbacks.stream().forEach(registry::register);
        return registry;
    }

    /**
     * The raw-string gateway over the SDK client — the only channel entity payloads travel on.
     *
     * @param client the shared SDK client
     * @param config the client's configuration (source of url/key for helper HTTP calls)
     * @return the gateway bean
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliRawGateway meiliRawGateway(Client client, Config config) {
        return new SdkMeiliRawGateway(client, config);
    }

    /**
     * The operations template composing gateway, metamodel, serializer and callbacks under the
     * configured write-await policy.
     *
     * @param gateway    raw-string gateway
     * @param context    entity metamodel cache
     * @param serializer document serializer
     * @param callbacks  lifecycle registry
     * @param properties the bound {@code meili.*} values (wait-task / wait-timeout)
     * @return the operations template
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliSearchOperations meiliSearchOperations(MeiliRawGateway gateway, MeiliMappingContext context,
                                                MeiliDocumentSerializer serializer,
                                                MeiliEntityCallbacks callbacks,
                                                MeiliProperties properties) {
        return new DefaultMeiliSearchOperations(gateway, context, serializer, callbacks,
                properties.isWaitTask(), properties.getWaitTimeout());
    }
}
