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

import io.github.lamspace.meili.autoconfigure.MeiliDataAutoConfiguration;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Optional auto configuration that hands document serialization to Jackson 3.
 *
 * <p>Activation contract ("add the dependency and it takes over"): the module ships its own
 * {@code AutoConfiguration.imports} entry; ordering places it <em>before</em>
 * {@link MeiliDataAutoConfiguration}, and the serializer bean is
 * {@link ConditionalOnMissingBean}-backed. On a Boot 4 generation runtime the Jackson 3
 * {@link ObjectMapper} class is present, so this configuration registers the Jackson 3
 * serializer first and the data-layer configuration backs off its own default. On a Boot 3
 * runtime the class condition is evaluated from bytecode metadata without loading the
 * Jackson 3 types, so the module is inert even if it sits on the classpath. A user-declared
 * {@link MeiliDocumentSerializer} bean wins over both.
 *
 * <p>Mapper selection mirrors the Jackson 2 default implementation: a container-provided
 * Jackson 3 {@link ObjectMapper} is used as the configuration base (never mutated), and a
 * private {@link JsonMapper} is built when none exists.
 */
@AutoConfiguration(before = MeiliDataAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
public class MeiliJackson3SerializerAutoConfiguration {

    /**
     * Instantiated by the auto-configuration machinery; all behavior lives in the bean method.
     */
    public MeiliJackson3SerializerAutoConfiguration() {
    }

    /**
     * The Jackson 3 document serializer, registered ahead of (and thereby backing off) the
     * data-layer default serializer.
     *
     * @param objectMappers provider for an optional container Jackson 3 ObjectMapper
     * @return the serializer used for every entity read/write
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliDocumentSerializer meiliDocumentSerializer(ObjectProvider<ObjectMapper> objectMappers) {
        return new Jackson3DocumentSerializer(objectMappers.getIfAvailable(JsonMapper::new));
    }
}
