package io.github.lamspace.meili.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 真机 IT（服务连接场景二"用户连接详情 bean 优先"）：测试上下文同时声明用户自有
 * {@link MeiliConnectionDetails} bean 与 {@code @ServiceConnection} 容器时，桥接退避，
 * {@link Config} 使用用户 bean 的连接信息。
 *
 * <p>退避的验收面是最终装配事实：Config 的 URL/密钥来自用户 bean，且上下文中
 * {@link MeiliConnectionDetails} 类型仅存用户一个实例（桥接 bean 不残留，
 * 避免下游按类型注入歧义）。
 */
@SpringBootTest(classes = BridgeBackoffApp.class, properties = "meili.index.auto-init=none")
class MeiliServiceConnectionBackoffIT {

    /** 同一机制接管容器，但其桥接结果应被用户自有 bean 压制。 */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** 装配结果观测点：必须携带用户 bean 的连接信息。 */
    @Autowired
    private Config config;

    /** 退避后类型唯一性断言的查询面。 */
    @Autowired
    private ApplicationContext context;

    /**
     * 用户自有 bean 优先：Config 落在用户 URL/密钥上，桥接不残留同类 bean。
     */
    @Test
    void userConnectionDetailsBeanWinsOverTheBridge() {
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(BridgeBackoffApp.USER_URL);
        assertThat(config.getApiKey()).isEqualTo(BridgeBackoffApp.USER_KEY);
        assertThat(context.getBeanNamesForType(MeiliConnectionDetails.class))
                .containsExactly("userMeiliConnectionDetails");
    }
}
