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
