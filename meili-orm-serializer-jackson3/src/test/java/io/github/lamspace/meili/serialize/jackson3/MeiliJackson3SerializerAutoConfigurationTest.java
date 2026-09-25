package io.github.lamspace.meili.serialize.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * L2 条件装配测试：Jackson3 序列化接管的"加依赖即生效、用户 bean 即让位、容器 mapper 优先"
 * 三条契约，全部在 ApplicationContextRunner 隔离上下文中判定，无需 Boot 应用或网络。
 */
class MeiliJackson3SerializerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MeiliJackson3SerializerAutoConfiguration.class);

    @Test
    @DisplayName("无用户 serializer 时接管为 Jackson3 实现")
    void takesOverSerializer() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MeiliDocumentSerializer.class);
            assertThat(ctx.getBean(MeiliDocumentSerializer.class))
                    .isInstanceOf(Jackson3DocumentSerializer.class);
        });
    }

    @Test
    @DisplayName("用户自定义 serializer 让位：Jackson3 实现不注册")
    void userBeanWins() {
        MeiliDocumentSerializer stub = new MeiliDocumentSerializer() {
            @Override
            public String write(Object document) {
                return "{}";
            }

            @Override
            public <T> T read(String json, Class<T> type) {
                throw new UnsupportedOperationException("stub");
            }
        };
        runner.withBean(MeiliDocumentSerializer.class, () -> stub).run(ctx -> {
            assertThat(ctx).hasSingleBean(MeiliDocumentSerializer.class);
            assertThat(ctx.getBean(MeiliDocumentSerializer.class))
                    .isSameAs(stub)
                    .isNotInstanceOf(Jackson3DocumentSerializer.class);
        });
    }

    @Test
    @DisplayName("容器 Jackson 3 mapper 作为底：用户命名策略被尊重")
    void containerObjectMapperPreferred() {
        record Book(@MeiliId Long id, @MeiliField(name = "book_title") String title,
                    String originalLanguage) {}
        runner.withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build())
                .run(ctx -> {
                    MeiliDocumentSerializer serializer = ctx.getBean(MeiliDocumentSerializer.class);
                    String json = serializer.write(new Book(1L, "三体", "Chinese"));
                    assertThat(json).contains("book_title").contains("original_language");
                });
    }
}
