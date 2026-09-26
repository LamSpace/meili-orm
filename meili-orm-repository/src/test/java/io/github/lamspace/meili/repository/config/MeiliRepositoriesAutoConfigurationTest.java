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
package io.github.lamspace.meili.repository.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.autoconfigure.MeiliClientAutoConfiguration;
import io.github.lamspace.meili.autoconfigure.MeiliDataAutoConfiguration;
import io.github.lamspace.meili.repository.l2fixture.BookRepository;
import io.github.lamspace.meili.repository.l2fixture.L2App;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * L2: the repository auto-configuration condition chain — fallback enablement, the property
 * switch, backing off when enable is explicit, silent skip when the data layer is absent,
 * loadable imports, and registered property metadata.
 */
class MeiliRepositoriesAutoConfigurationTest {

    @Configuration
    @EnableMeiliRepositories(basePackages = "io.github.lamspace.meili.repository.l2fixture")
    static class ExplicitEnable {
    }

    /** Full chain of data layer + repository fallback (the Client constructor issues no requests, so the url may point at an unreachable port). */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MeiliClientAutoConfiguration.class,
                    MeiliDataAutoConfiguration.class,
                    MeiliRepositoriesAutoConfiguration.class))
            .withPropertyValues("meili.url=http://localhost:1", "meili.api-key=k");

    @Test
    void fallbackScansAutoConfigurationPackages() {
        runner.withUserConfiguration(L2App.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(BookRepository.class));
    }

    @Test
    void disabledSwitchRemovesRepositoriesButKeepsDataLayer() {
        runner.withUserConfiguration(L2App.class)
                .withPropertyValues("meili.repositories.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(BookRepository.class);
                    assertThat(ctx).hasNotFailed();
                    // the data layer is unaffected by this switch
                    assertThat(ctx).hasSingleBean(io.github.lamspace.meili.core.operations.MeiliSearchOperations.class);
                });
    }

    @Test
    void explicitEnableWinsWithoutDoubleRegistration() {
        runner.withUserConfiguration(L2App.class, ExplicitEnable.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(BookRepository.class);
                    assertThat(ctx.getBeanNamesForType(BookRepository.class)).hasSize(1);
                });
    }

    @Test
    void dataLayerAbsentSkipsSilently() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MeiliRepositoriesAutoConfiguration.class))
                .withUserConfiguration(L2App.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(BookRepository.class);
                    assertThat(ctx).hasNotFailed();
                });
    }

    @Test
    void nonBootTestPackageStillSkippedWithoutFailure() {
        // Plain context without @AutoConfigurationPackage: fallback skipped (DEBUG), no failure thrown
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MeiliClientAutoConfiguration.class,
                        MeiliDataAutoConfiguration.class,
                        MeiliRepositoriesAutoConfiguration.class))
                .withPropertyValues("meili.url=http://localhost:1")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(BookRepository.class));
    }

    @Test
    void importsFileListsLoadableAutoConfiguration() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            assertThat(lines).containsExactly(
                    "io.github.lamspace.meili.repository.config.MeiliRepositoriesAutoConfiguration");
            for (String line : lines) {
                Class.forName(line);
            }
        }
    }

    @Test
    void repositoriesEnabledPropertyDocumentedInMetadata() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring-configuration-metadata.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(json).contains("meili.repositories.enabled");
        }
    }
}
