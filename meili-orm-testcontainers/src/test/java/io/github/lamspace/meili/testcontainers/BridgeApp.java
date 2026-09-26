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

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal application host for the bridge IT: enables auto configuration only; all connection
 * information is supplied by the service-connection bridge.
 *
 * <p>Deliberately not {@code @SpringBootApplication}: it would bring component scanning, and
 * this package also holds the backoff scenario's other host class and entities — hosts must
 * not scan each other. {@code @SpringBootConfiguration} itself is not a component annotation,
 * so coexisting in one package is safe; the auto-configuration package (the entity scan base)
 * is delimited by this class's package.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class BridgeApp {
}
