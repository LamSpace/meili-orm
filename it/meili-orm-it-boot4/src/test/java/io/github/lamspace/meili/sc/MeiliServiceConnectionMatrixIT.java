package io.github.lamspace.meili.sc;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.testcontainers.MeiliSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 矩阵真机 IT（服务连接场景一）：{@code @ServiceConnection} 声明的
 * {@link MeiliSearchContainer} 经桥接装配出指向活容器的 {@link Config}，
 * 保存后按主键读取往返成功。
 *
 * <p>本类与 Boot 4.0.3 矩阵模块的同名类逐字节共用（唯一差异是版本哨兵期望值，
 * 模块实际钉住的代际由哨兵自证）：同一份 meili-orm-testcontainers 字节码必须
 * 在两代 classpath 下产出一致的桥接行为——单模块双包形态的端到端证据。
 */
@SpringBootTest(classes = ServiceConnectionApp.class, properties = "meili.wait-task=true")
class MeiliServiceConnectionMatrixIT {

    /** 被服务连接接管的容器：静态字段复用，上下文缓存期内只启动一次。 */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** 桥接产出的连接详情 bean。 */
    @Autowired
    private MeiliConnectionDetails details;

    /** 由桥接详情构建的 SDK 配置：构建前观测 URL 与密钥的落点。 */
    @Autowired
    private Config config;

    /** 装配链末端的数据操作面。 */
    @Autowired
    private MeiliSearchOperations operations;

    /**
     * 版本哨兵：证明本模块实际运行在它被钉住的那一代 Boot 上，矩阵没有静默换代。
     */
    @Test
    void classpathIsThePinnedGeneration() {
        assertThat(SpringBootVersion.getVersion())
                .as("矩阵版本钉定漂移：本模块运行 classpath 与钉版不符")
                .startsWith("4.");
    }

    /**
     * 桥接 bean 的 URL/密钥与容器实际映射端口及所配密钥一致，且 Config 携带同一份值。
     */
    @Test
    void configIsBuiltFromTheLiveContainer() {
        assertThat(details.getUrl()).isEqualTo(CONTAINER.getUrl());
        assertThat(details.getApiKey()).isEqualTo(CONTAINER.getApiKey());
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(CONTAINER.getUrl());
        assertThat(config.getApiKey()).isEqualTo(CONTAINER.getApiKey());
    }

    /**
     * 保存后按主键读取往返成功（真机、经装配链、零手写连接配置）。
     */
    @Test
    void saveThenReadByIdRoundTripsThroughTheContainer() {
        operations.save(new ServiceConnectionBook(7L, "矩阵桥接往返"));

        assertThat(operations.findById(7L, ServiceConnectionBook.class))
                .hasValueSatisfying(book -> assertThat(book.title()).isEqualTo("矩阵桥接往返"));
    }
}
