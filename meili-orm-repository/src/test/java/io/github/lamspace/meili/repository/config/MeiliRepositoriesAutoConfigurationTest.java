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
 * L2：仓库自动配置条件链——兜底启用、属性开关、显式 enable 让位、数据层缺席静默跳过、
 * imports 可加载、属性元数据在册。
 */
class MeiliRepositoriesAutoConfigurationTest {

    @Configuration
    @EnableMeiliRepositories(basePackages = "io.github.lamspace.meili.repository.l2fixture")
    static class ExplicitEnable {
    }

    /** 数据层 + 仓库兜底全链路（Client 构造不发请求，url 可指向不可达端口）。 */
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
                    // 数据层不受本开关影响
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
        // 无 @AutoConfigurationPackage 的普通上下文：兜底跳过（DEBUG），不抛错
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
