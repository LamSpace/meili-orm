package io.github.lamspace.meili.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import io.github.lamspace.meili.serialize.jackson3.Jackson3DocumentSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boot 4 + Jackson 3 opt-in 矩阵 IT：可选序列化模块"加依赖即接管"的真机端到端证明。
 *
 * <p>复用基线 Boot4 矩阵的 {@link ItApp}/{@link ITBook} 测试壳（test-jar）；与基线的
 * 唯一差异是本模块 classpath 上存在 meili-orm-serializer-jackson3——自动配置排序使其
 * 先于数据层注册 Jackson3 序列化器。断言接管生效且 Long 精度往返不因此回退；
 * "类缺席则不接管"的分支由基线 it-boot4 与 it-boot3 矩阵证明，不在本模块重复。
 */
@SpringBootTest(classes = ItApp.class)
class Jackson3WiringIT {

    /** 超 2^53 的探针主键：Jackson3 通道同样必须逐位无损。 */
    private static final long LOSSY_ABOVE_DOUBLE_ID = 9007199254740993L;

    @Autowired
    private MeiliSearchOperations operations;

    @Autowired
    private MeiliDocumentSerializer serializer;

    /**
     * 与基线矩阵同构的上下文属性：钉版容器 + 同步写 + sync-settings/apply。
     *
     * @param registry 属性注册器
     */
    @DynamicPropertySource
    static void meiliProperties(DynamicPropertyRegistry registry) {
        registry.add("meili.url", MeiliContainer::url);
        registry.add("meili.api-key", () -> MeiliContainer.MASTER_KEY);
        registry.add("meili.wait-task", () -> "true");
        registry.add("meili.index.auto-init", () -> "sync-settings");
        registry.add("meili.index.on-settings-drift", () -> "apply");
    }

    /**
     * 接管哨兵：类在 classpath 即换为 Jackson3 实现（依赖缺席分支由基线矩阵覆盖）。
     */
    @Test
    void serializerIsTakenOverByJackson3() {
        assertThat(SpringBootVersion.getVersion())
                .as("矩阵版本钉定漂移：本模块运行 classpath 与钉版不符")
                .startsWith("4.");
        assertThat(serializer).isInstanceOf(Jackson3DocumentSerializer.class);
    }

    /**
     * Jackson3 通道下的真机 CRUD 往返：实体经 raw 通道读写，Long 主键逐位无损。
     */
    @Test
    void jackson3ChannelRoundTripsAgainstRealServer() {
        operations.save(new ITBook(LOSSY_ABOVE_DOUBLE_ID, "活着", 26.0));

        assertThat(operations.findById(LOSSY_ABOVE_DOUBLE_ID, ITBook.class))
                .hasValueSatisfying(b -> assertThat(b.id()).isEqualTo(LOSSY_ABOVE_DOUBLE_ID));

        MeiliSearchResult<ITBook> result = operations.search("活着", ITBook.class);
        assertThat(result.getHits())
                .extracting(ITBook::id)
                .contains(LOSSY_ABOVE_DOUBLE_ID);
    }
}
