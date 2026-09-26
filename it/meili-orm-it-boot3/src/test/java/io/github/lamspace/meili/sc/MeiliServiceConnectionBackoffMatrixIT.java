package io.github.lamspace.meili.sc;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import io.github.lamspace.meili.testcontainers.MeiliSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 矩阵真机 IT（服务连接场景二"用户连接详情 bean 优先"）：用户自有
 * {@link MeiliConnectionDetails} bean 与 {@code @ServiceConnection} 容器共存时，
 * 桥接退避、{@link Config} 使用用户 bean 的连接信息。
 *
 * <p>与 Boot 4.0.3 矩阵模块同名类逐字节共用（仅版本哨兵期望值不同）。退避由
 * meili-orm-testcontainers 的自动配置守卫在两代下同样生效（退避机制只用
 * 双代一致的 bean 定义属性标记），本类即该机制的双代端到端证据。
 */
@SpringBootTest(classes = ServiceConnectionBackoffApp.class, properties = "meili.index.auto-init=none")
class MeiliServiceConnectionBackoffMatrixIT {

    /** 同一机制接管容器，但桥接结果应被用户自有 bean 压制（容器因此无需启动）。 */
    @ServiceConnection
    private static final MeiliSearchContainer CONTAINER = new MeiliSearchContainer();

    /** 装配结果观测点：必须携带用户 bean 的连接信息。 */
    @Autowired
    private Config config;

    /** 退避后类型唯一性断言的查询面。 */
    @Autowired
    private ApplicationContext context;

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
     * 用户自有 bean 优先：Config 落在用户 URL/密钥上，桥接不残留同类 bean。
     */
    @Test
    void userConnectionDetailsBeanWinsOverTheBridge() {
        assertThat(config.getHostUrl().replaceAll("/$", "")).isEqualTo(ServiceConnectionBackoffApp.USER_URL);
        assertThat(config.getApiKey()).isEqualTo(ServiceConnectionBackoffApp.USER_KEY);
        assertThat(context.getBeanNamesForType(MeiliConnectionDetails.class))
                .containsExactly("userMeiliConnectionDetails");
    }
}
