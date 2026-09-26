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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.meilisearch.sdk.Client;
import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L2 conditions-chain tests for the data-layer auto configuration: core bean wiring, the
 * {@code Client}-present precondition, ObjectMapper preference, serializer back-off and
 * callback collection. Written test-first against {@link MeiliDataAutoConfiguration}.
 */
class MeiliDataAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MeiliClientAutoConfiguration.class, MeiliDataAutoConfiguration.class)
            .withPropertyValues("meili.url=http://localhost:1", "meili.api-key=k");

    /** @MeiliDocument probe entity. */
    @MeiliDocument(indexName = "probe_books")
    record Probe(@MeiliId Long id, String title) {
    }

    /** Snake-case probe entity. */
    record Money(@MeiliId Long id, String unitPrice) {
    }

    @Test
    void wiresCoreBeans() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MeiliSearchOperations.class);
            assertThat(ctx).hasSingleBean(MeiliMappingContext.class);
            assertThat(ctx).hasSingleBean(MeiliRawGateway.class);
            assertThat(ctx).hasSingleBean(MeiliEntityCallbacks.class);
            assertThat(ctx.getBean(MeiliDocumentSerializer.class)).isInstanceOf(Jackson2DocumentSerializer.class);
        });
    }

    @Test
    void dataLayerBacksOffWithoutClient() {
        new ApplicationContextRunner()
                .withUserConfiguration(MeiliDataAutoConfiguration.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MeiliSearchOperations.class));
    }

    @Test
    void disabledSwitchRemovesDataLayerToo() {
        runner.withPropertyValues("meili.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(Client.class);
                    assertThat(ctx).doesNotHaveBean(MeiliSearchOperations.class);
                    assertThat(ctx).doesNotHaveBean(MeiliDocumentSerializer.class);
                });
    }

    @Test
    void containerObjectMapperPreferred() {
        ObjectMapper custom = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        runner.withBean(ObjectMapper.class, () -> custom)
                .run(ctx -> {
                    MeiliDocumentSerializer serializer = ctx.getBean(MeiliDocumentSerializer.class);
                    assertThat(serializer.write(new Money(1L, "9.9"))).contains("unit_price");
                });
    }

    @Test
    void builtinSerializerDefaultsAreStable() {
        runner.run(ctx -> {
            MeiliDocumentSerializer serializer = ctx.getBean(MeiliDocumentSerializer.class);
            String json = serializer.write(new Probe(9007199254740993L, "x"));
            assertThat(json).contains("9007199254740993");
            assertThat(serializer.read(json, Probe.class).id()).isEqualTo(9007199254740993L);
            assertThat(serializer.read("{\"id\":7,\"zzz\":2}", Probe.class).id()).isEqualTo(7L);
        });
    }

    @Test
    void serializerBacksOffToUserBean() {
        MeiliDocumentSerializer custom = new MeiliDocumentSerializer() {
            @Override
            public String write(Object document) {
                throw new UnsupportedOperationException("user serializer");
            }

            @Override
            public <T> T read(String json, Class<T> type) {
                throw new UnsupportedOperationException("user serializer");
            }
        };
        runner.withBean(MeiliDocumentSerializer.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx.getBean(MeiliDocumentSerializer.class)).isSameAs(custom);
                    assertThat(ctx).hasSingleBean(MeiliSearchOperations.class);
                });
    }

    /**
     * Callback bean in the shape real users must supply: a concrete class whose declared
     * interface carries the entity type (a raw lambda erases it and core rejects it at
     * registration, which surfaces here as a startup failure by design).
     */
    static class ProbeNormalizer implements BeforeConvertCallback<Probe> {
        @Override
        public Probe onBeforeConvert(Probe entity, String indexName) {
            return entity;
        }
    }

    @Test
    void callbackBeansAreCollected() {
        runner.withBean(ProbeNormalizer.class, ProbeNormalizer::new)
                .run(ctx -> assertThat(ctx.getBean(MeiliEntityCallbacks.class).registeredCount())
                        .isGreaterThan(0));
    }

    @Test
    void noCallbackBeansMeansEmptyRegistry() {
        runner.run(ctx -> assertThat(ctx.getBean(MeiliEntityCallbacks.class).registeredCount()).isZero());
    }
}
