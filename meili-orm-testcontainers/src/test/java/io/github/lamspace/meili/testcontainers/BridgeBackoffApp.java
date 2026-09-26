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
package io.github.lamspace.meili.testcontainers;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Backoff-scenario host: declares a user-owned {@link MeiliConnectionDetails} bean in addition
 * to the bridge; the user bean's connection information must ultimately reach the Config.
 *
 * <p>Index initialization is off: this scenario performs no real read/write, it only needs the
 * wiring result to be assertable.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class BridgeBackoffApp {

    /**
     * Fixed URL of the user bean, clearly different from the service container address
     * so assertions can tell the winner.
     */
    static final String USER_URL = "http://user-provided.example:1";

    /** Fixed API key of the user bean. */
    static final String USER_KEY = "user-key";

    /**
     * User-owned connection details: per the auto-configuration contract this must make the
     * properties-based implementation back off, and the service-connection bridge too.
     *
     * @return the user implementation supplying the fixed connection information
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
