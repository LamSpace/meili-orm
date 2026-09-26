package io.github.lamspace.meili.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

/**
 * 真机 IT：默认容器健康就绪与镜像/密钥覆盖在活的 MeiliSearch 实例上成立。
 *
 * <p>判定面：{@code /health} 返回 200 即就绪（容器等待条件），受保护端点按所配
 * master key 认证（正确密钥 200、错误密钥 403）证明密钥确实注入。两个场景各自
 * 起停一次性容器，Ryuk 负责回收。
 */
class MeiliSearchContainerIT {

    /** JDK HTTP 客户端：不引入额外测试依赖即可断言健康与认证行为。 */
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * 场景"默认容器健康就绪"：就绪后 URL 可达健康端点，认证使用所配 master key。
     */
    @Test
    void defaultContainerBecomesHealthyAndAuthenticatesWithMasterKey() {
        try (MeiliSearchContainer container = new MeiliSearchContainer()) {
            container.start();

            assertThat(status(container, container.getUrl() + "/health", null)).isEqualTo(200);
            assertThat(status(container, container.getUrl() + "/indexes", container.getApiKey())).isEqualTo(200);
            assertThat(status(container, container.getUrl() + "/indexes", "wrong-key")).isEqualTo(403);
        }
    }

    /**
     * 场景"覆盖生效"：显式镜像构造路径与自定义密钥在活实例上被服务端行为证实
     * （自定义 key 通过认证、默认 key 被拒）。
     */
    @Test
    void overriddenImageAndKeyTakeEffectOnLiveContainer() {
        DockerImageName image = DockerImageName.parse("getmeili/meilisearch:v1.49.0");
        try (MeiliSearchContainer container =
                     new MeiliSearchContainer(image).withMasterKey("override-key-live")) {
            container.start();

            assertThat(container.getDockerImageName()).isEqualTo(image.asCanonicalNameString());
            assertThat(container.getApiKey()).isEqualTo("override-key-live");
            assertThat(status(container, container.getUrl() + "/indexes", "override-key-live")).isEqualTo(200);
            assertThat(status(container, container.getUrl() + "/indexes",
                    MeiliSearchContainer.DEFAULT_MASTER_KEY)).isEqualTo(403);
        }
    }

    /**
     * 发送 GET 并返回响应状态码。
     *
     * @param container 目标容器（保证请求期间存活）
     * @param url       完整请求 URL
     * @param apiKey    Bearer 密钥；{@code null} 表示不携带认证头
     * @return HTTP 状态码
     */
    private static int status(MeiliSearchContainer container, String url, String apiKey) {
        assertThat(container.isRunning()).as("容器须处于运行态").isTrue();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (apiKey != null) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
