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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.repository.config.fixture.FxBook;
import io.github.lamspace.meili.repository.config.fixture.FixtureBookRepository;
import io.github.lamspace.meili.repository.config.fixture.MarkerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * L2-lite: the three paths of {@code @EnableMeiliRepositories} — explicit basePackages,
 * default = the annotated class's package, {@code @NoRepositoryBean} exclusion — plus by-type
 * injection.
 */
class MeiliRepositoriesRegistrarTest {

    @Configuration
    @EnableMeiliRepositories(basePackages = "io.github.lamspace.meili.repository.config.fixture")
    @Import(Infra.class)
    static class ExplicitConfig {
    }

    @Configuration
    @EnableMeiliRepositories
    @Import(Infra.class)
    static class DefaultPackageConfig {
    }

    /** The annotated class sits in the fixture package: the default scan should hit that package (the test lives in the outer package, expanded via the nested-class path). */
    @Configuration
    static class Infra {
        @Bean
        MeiliSearchOperations meiliSearchOperations() {
            MeiliSearchOperations ops = mock(MeiliSearchOperations.class);
            when(ops.count(FxBook.class)).thenReturn(2L);
            return ops;
        }

        @Bean
        MeiliMappingContext meiliMappingContext() {
            return new MeiliMappingContext();
        }
    }

    @Test
    void explicitBasePackagesRegistersUsableRepository() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(ExplicitConfig.class)) {
            FixtureBookRepository repo = ctx.getBean(FixtureBookRepository.class);
            assertThat(repo.count()).isEqualTo(2L);
            assertThat(ctx.getBeanNamesForType(FixtureBookRepository.class)).hasSize(1);
        }
    }

    @Test
    void noRepositoryBeanInterfaceIsNotRegistered() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(ExplicitConfig.class)) {
            assertThat(ctx.getBeanNamesForType(MarkerRepository.class)).isEmpty();
        }
    }

    @Test
    void defaultPackagesFallBackToAnnotatedClassPackage() {
        // Annotated class in the config package: the default scan covers config (including the fixture sub-package) →
        // only the fixture interface registers; bean names follow the Spring Data convention (decapitalized simple
        // interface name), and @NoRepositoryBean never appears.
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(DefaultPackageConfig.class)) {
            assertThat(ctx.getBean(FixtureBookRepository.class)).isNotNull();
            assertThat(ctx.getBeanNamesForType(FixtureBookRepository.class))
                    .containsExactly("fixtureBookRepository");
            assertThat(ctx.containsBean("markerRepository")).isFalse();
        }
    }
}
