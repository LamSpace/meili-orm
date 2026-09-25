package io.github.lamspace.meili.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 矩阵 IT：starter 在真机（钉版 v1.49.0 容器）上的装配与 CRUD 往返。
 *
 * <p>本类在 Boot 3.5.16 与 4.0.3 两代矩阵模块间逐字节共用（唯一差异是版本哨兵
 * 期望值，模块实际钉住的代际由该哨兵自证）：两代 classpath 下装配链、raw 读通道
 * 与 Long 精度契约必须等价。容器由 core test-jar 的 {@link MeiliContainer}
 * 静态单例承载；写路径配置为等待任务终态，读断言无竞态。
 */
@SpringBootTest(classes = ItApp.class)
class MeiliStarterIT {

    /** 超 2^53 的探针主键：任何 Gson/Double 中转都会在此丢精度。 */
    private static final long LOSSY_ABOVE_DOUBLE_ID = 9007199254740993L;

    @Autowired
    private MeiliSearchOperations operations;

    @Autowired
    private MeiliDocumentSerializer serializer;

    /**
     * 把钉版容器的连接参数与同步写策略注入测试上下文。
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
     * 版本哨兵：证明本模块实际运行在它被钉住的那一代 Boot 上，矩阵没有静默换代。
     */
    @Test
    void classpathIsThePinnedGeneration() {
        assertThat(SpringBootVersion.getVersion())
                .as("矩阵版本钉定漂移：本模块运行 classpath 与钉版不符")
                .startsWith("3.");
    }

    /**
     * starter 装配 + 写读搜往返：默认序列化器为 Jackson2；启动 initializer 建索引；
     * Long 主键经 save→findById→search 全链路逐位无损。
     */
    @Test
    void starterWiresAndRoundTripsLongKeyedEntity() {
        assertThat(serializer).isInstanceOf(Jackson2DocumentSerializer.class);
        assertThat(operations.indexExists(ITBook.class)).isTrue();

        operations.save(new ITBook(LOSSY_ABOVE_DOUBLE_ID, "三体", 59.0));

        assertThat(operations.findById(LOSSY_ABOVE_DOUBLE_ID, ITBook.class))
                .hasValueSatisfying(b -> assertThat(b.id()).isEqualTo(LOSSY_ABOVE_DOUBLE_ID));

        MeiliSearchResult<ITBook> result = operations.search("三体", ITBook.class);
        assertThat(result.getHits())
                .extracting(ITBook::id)
                .contains(LOSSY_ABOVE_DOUBLE_ID);
    }
}
