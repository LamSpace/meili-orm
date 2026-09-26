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
 * L2-lite：{@code @EnableMeiliRepositories} 三条路径——显式 basePackages、缺省=注解类包、
 * {@code @NoRepositoryBean} 排除，及 by-type 注入。
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

    /** 注解类位于 fixture 包：缺省扫描应命中该包（测试放外层，经内部类路径展开）。 */
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
        // 注解类在 config 包：缺省扫描 config（含 fixture 子包）→ 仅注册 fixture 接口，
        // bean 名沿用 Spring Data 惯例（接口简名 decapitalize），@NoRepositoryBean 不出现。
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(DefaultPackageConfig.class)) {
            assertThat(ctx.getBean(FixtureBookRepository.class)).isNotNull();
            assertThat(ctx.getBeanNamesForType(FixtureBookRepository.class))
                    .containsExactly("fixtureBookRepository");
            assertThat(ctx.containsBean("markerRepository")).isFalse();
        }
    }
}
