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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

/**
 * Configuration assertions: the container's default pinned image and the "image/key
 * overridable" contract are observable at construction time, with no Docker dependency.
 */
class MeiliSearchContainerTest {

    /**
     * Default construction: image pinned to v1.49.0, port 7700 exposed, and the master key
     * environment variable matches the default key.
     */
    @Test
    void defaultsPinImagePortAndMasterKey() {
        MeiliSearchContainer container = new MeiliSearchContainer();

        assertThat(container.getConfiguredImage().asCanonicalNameString())
                .isEqualTo("getmeili/meilisearch:v1.49.0");
        assertThat(container.getExposedPorts()).containsExactly(MeiliSearchContainer.MEILISEARCH_PORT);
        assertThat(container.getEnvMap())
                .containsEntry("MEILI_MASTER_KEY", MeiliSearchContainer.DEFAULT_MASTER_KEY);
        assertThat(container.getApiKey()).isEqualTo(MeiliSearchContainer.DEFAULT_MASTER_KEY);
    }

    /**
     * Overrides take effect: both a custom image tag (any tag in the same repository) and an
     * image relocation (a renamed repository must declare compatibility via
     * {@code asCompatibleSubstituteFor}) work, and the key is freely replaceable; the value
     * read back is exactly the value set. Nothing touches Docker throughout
     * ({@link MeiliSearchContainer#getConfiguredImage()} is a pure configuration read with no
     * image resolution).
     */
    @Test
    void imageAndMasterKeyAreOverridable() {
        MeiliSearchContainer customTag =
                new MeiliSearchContainer("getmeili/meilisearch:v9.9.9-unstarted").withMasterKey("override-key-9");

        assertThat(customTag.getConfiguredImage().asCanonicalNameString())
                .isEqualTo("getmeili/meilisearch:v9.9.9-unstarted");
        assertThat(customTag.getApiKey()).isEqualTo("override-key-9");
        assertThat(customTag.getEnvMap()).containsEntry("MEILI_MASTER_KEY", "override-key-9");

        DockerImageName mirror = DockerImageName
                .parse("registry.example.internal/mirror/meili:v1.49.0")
                .asCompatibleSubstituteFor("getmeili/meilisearch");
        assertThat(new MeiliSearchContainer(mirror).getConfiguredImage().asCanonicalNameString())
                .isEqualTo("registry.example.internal/mirror/meili:v1.49.0");
    }
}
