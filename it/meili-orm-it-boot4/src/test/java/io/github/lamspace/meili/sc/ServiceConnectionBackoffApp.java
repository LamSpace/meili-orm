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
package io.github.lamspace.meili.sc;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Host for the matrix backoff scenario: a user-owned {@link MeiliConnectionDetails} bean coexists
 * with a {@code @ServiceConnection} container, and the assembly result must carry the user bean's
 * connection info.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class ServiceConnectionBackoffApp {

    /** Fixed URL for the user bean, clearly distinct from the container address to make the take/supersede assertion easy. */
    static final String USER_URL = "http://user-provided.example:1";

    /** Fixed API key for the user bean. */
    static final String USER_KEY = "user-key";

    /**
     * User-owned connection details: should force the bridge to back off (the property defaults
     * back off along with it).
     *
     * @return the user implementation carrying fixed connection info
     */
    @Bean
    MeiliConnectionDetails userMeiliConnectionDetails() {
        return new MeiliConnectionDetails() {
            @Override
            public String getUrl() {
                return USER_URL;
            }

            @Override
            public String getApiKey() {
                return USER_KEY;
            }
        };
    }
}
