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
/**
 * meili-orm Testcontainers integration: a typed MeiliSearch service container
 * ({@link io.github.lamspace.meili.testcontainers.MeiliSearchContainer}) and the Spring Boot
 * service-connection bridge
 * ({@link io.github.lamspace.meili.testcontainers.MeiliContainerConnectionDetailsFactory}).
 *
 * <p>The bridge only produces a
 * {@link io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails}; the downstream client
 * and data wiring chains reuse the auto-configuration behavior entirely.</p>
 *
 * <p>References only the service-connection factory SPI and annotation infrastructure that are
 * stable across both the Boot 3.5.x and 4.x generations
 * ({@code org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory}
 * and {@code org.springframework.boot.testcontainers.service.connection.*}); the compilation
 * baseline is the lowest supported generation.</p>
 */
package io.github.lamspace.meili.testcontainers;
