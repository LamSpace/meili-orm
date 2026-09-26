package io.github.lamspace.meili.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真机 IT（服务连接场景一"容器接入上下文完成往返"）：静态字段加
 * {@code @ServiceConnection} 即让桥接注册 {@link MeiliConnectionDetails}，装配出的
 * SDK {@link Config} 携带容器 URL 与密钥（构建前观测），保存后按主键读取往返成功。
 *
 * <p>上下文不写任何 {@code meili.url}/{@code meili.api-key} 属性——连接信息的唯一
 * 来源是被桥接的容器，这正是"一行注解"语法糖的验收面。写路径开等待任务终态，
 * 读断言无竞态；索引由启动期 create-if-missing 建立。
 */
@SpringBootTest(classes = BridgeApp.class, properties = "meili.wait-task=true")
class MeiliServiceConnectionIT {

    /** 被服务连接接管的容器：静态字段复用，上下文缓存期内只启动一次。 */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** 桥接产出的连接详情 bean（属性实现应因它退避）。 */
    @Autowired
    private MeiliConnectionDetails details;

    /** 由桥接详情构建的 SDK 配置：构建前观测 URL 与密钥的落点。 */
    @Autowired
    private Config config;

    /** 装配链末端的数据操作面：往返读写的入口。 */
    @Autowired
    private MeiliSearchOperations operations;

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
        operations.save(new BridgeBook(42L, "桥接往返"));

        assertThat(operations.findById(42L, BridgeBook.class))
                .hasValueSatisfying(book -> assertThat(book.title()).isEqualTo("桥接往返"));
    }
}
