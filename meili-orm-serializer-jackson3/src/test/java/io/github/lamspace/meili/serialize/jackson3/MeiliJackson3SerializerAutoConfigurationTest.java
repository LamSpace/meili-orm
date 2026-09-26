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
package io.github.lamspace.meili.serialize.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conditional-assembly tests for the Jackson 3 serializer takeover: the three contracts —
 * adding the dependency activates it, a user bean makes it back off, the container mapper is
 * preferred — are all judged inside isolated ApplicationContextRunner contexts, with no Boot
 * application or network required.
 */
class MeiliJackson3SerializerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MeiliJackson3SerializerAutoConfiguration.class);

    @Test
    @DisplayName("Takes over with the Jackson 3 implementation when no user serializer exists")
    void takesOverSerializer() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MeiliDocumentSerializer.class);
            assertThat(ctx.getBean(MeiliDocumentSerializer.class))
                    .isInstanceOf(Jackson3DocumentSerializer.class);
        });
    }

    @Test
    @DisplayName("User-defined serializer wins: the Jackson 3 implementation is not registered")
    void userBeanWins() {
        MeiliDocumentSerializer stub = new MeiliDocumentSerializer() {
            @Override
            public String write(Object document) {
                return "{}";
            }

            @Override
            public <T> T read(String json, Class<T> type) {
                throw new UnsupportedOperationException("stub");
            }
        };
        runner.withBean(MeiliDocumentSerializer.class, () -> stub).run(ctx -> {
            assertThat(ctx).hasSingleBean(MeiliDocumentSerializer.class);
            assertThat(ctx.getBean(MeiliDocumentSerializer.class))
                    .isSameAs(stub)
                    .isNotInstanceOf(Jackson3DocumentSerializer.class);
        });
    }

    @Test
    @DisplayName("Container Jackson 3 mapper as the base: the user's naming strategy is respected")
    void containerObjectMapperPreferred() {
        record Book(@MeiliId Long id, @MeiliField(name = "book_title") String title,
                    String originalLanguage) {}
        runner.withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build())
                .run(ctx -> {
                    MeiliDocumentSerializer serializer = ctx.getBean(MeiliDocumentSerializer.class);
                    String json = serializer.write(new Book(1L, "三体", "Chinese"));
                    assertThat(json).contains("book_title").contains("original_language");
                });
    }
}
