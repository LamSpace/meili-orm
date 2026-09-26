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

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal application host for the matrix service-connection IT: enables auto-configuration only
 * and leaves connection info entirely to the service-connection bridge.
 *
 * <p>Avoids {@code @SpringBootApplication}: component scanning would otherwise pull the sibling
 * backoff host in the same package into the context too.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class ServiceConnectionApp {
}
