package io.github.lamspace.meili.core;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 全工程共用的 MeiliSearch 测试容器（镜像钉死 v1.49.0，随 core test-jar 分发）。
 *
 * <p>线程模型：类加载时经静态初始化块启动单例容器，同一 JVM 内所有集成测试共享之；
 * 容器直至 JVM 退出由 Testcontainers Ryuk 回收，不做逐测试重启。</p>
 *
 * <p>硬前置（声明式，缺失即快速失败且不做静默跳过）：本机 Docker 守护进程可用，
 * 且本地已存在 {@code getmeili/meilisearch:v1.49.0} 镜像。守护进程不可用时，
 * 失败形态为 {@code NoClassDefFoundError}/{@code ExceptionInInitializerError}
 * （Testcontainers 客户端初始化异常），属环境问题而非用例缺陷。</p>
 *
 * <p>就绪条件：{@code /health} 返回 HTTP 200。master key 为固定测试值，仅对
 * 本容器内一次性数据生效，不得用于任何非测试场景。</p>
 */
public final class MeiliContainer {

    /** 容器内主密钥（测试专用固定值）。 */
    public static final String MASTER_KEY = "masterKey-test-123456";

    /** 钉死的镜像坐标：tag 精确到 v1.49.0，禁止 latest 或版本区间。 */
    public static final String IMAGE = "getmeili/meilisearch:v1.49.0";

    /** 单例容器：随机宿主端口映射到 7700，dev 模式 + 固定 master key。 */
    public static final GenericContainer<?> MEILI = new GenericContainer<>(
            DockerImageName.parse(IMAGE))
        .withExposedPorts(7700)
        .withEnv("MEILI_MASTER_KEY", MASTER_KEY)
        .withEnv("MEILI_ENV", "development")
        .waitingFor(Wait.forHttp("/health").forPort(7700).forStatusCode(200));

    static {
        MEILI.start();
    }

    private MeiliContainer() {
    }

    /**
     * 返回当前容器的基础 URL（{@code http://host:mappedPort}），
     * 供 SDK {@code Config} 或属性注入使用。
     *
     * @return 形如 {@code http://localhost:49152} 的服务地址
     */
    public static String url() {
        return "http://" + MEILI.getHost() + ":" + MEILI.getMappedPort(7700);
    }
}
