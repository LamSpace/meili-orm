package io.github.lamspace.meili.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

/**
 * L1 配置断言：容器的默认钉版与"镜像/密钥可覆盖"契约在构造期即可观察，不依赖 Docker。
 */
class MeiliSearchContainerTest {

    /**
     * 默认构造：镜像钉 v1.49.0、暴露 7700、master key 环境变量与默认密钥一致。
     */
    @Test
    void defaultsPinImagePortAndMasterKey() {
        MeiliSearchContainer container = new MeiliSearchContainer();

        assertThat(container.getConfiguredImage().asCanonicalNameString())
                .isEqualTo("getmeili/meilisearch:v1.49.0");
        assertThat(container.getExposedPorts()).containsExactly(MeiliSearchContainer.MEILISEARCH_PORT);
        assertThat(container.getEnvMap())
                .containsEntry("MEILI_MASTER_KEY", MeiliSearchContainer.DEFAULT_MASTER_KEY);
        assertThat(container.getApiKey()).isEqualTo(MeiliSearchContainer.DEFAULT_MASTER_KEY);
    }

    /**
     * 覆盖生效：自定义镜像标签（同仓库任意 tag）与镜像搬家（仓库改名须
     * {@code asCompatibleSubstituteFor} 声明兼容）两种路径都成立，密钥任意可换；
     * 读取值即自定义值。全程不触 Docker（{@link MeiliSearchContainer#getConfiguredImage()}
     * 为纯配置读取，不解析镜像）。
     */
    @Test
    void imageAndMasterKeyAreOverridable() {
        MeiliSearchContainer customTag =
                new MeiliSearchContainer("getmeili/meilisearch:v9.9.9-unstarted").withMasterKey("override-key-9");

        assertThat(customTag.getConfiguredImage().asCanonicalNameString())
                .isEqualTo("getmeili/meilisearch:v9.9.9-unstarted");
        assertThat(customTag.getApiKey()).isEqualTo("override-key-9");
        assertThat(customTag.getEnvMap()).containsEntry("MEILI_MASTER_KEY", "override-key-9");

        DockerImageName mirror = DockerImageName
                .parse("registry.example.internal/mirror/meili:v1.49.0")
                .asCompatibleSubstituteFor("getmeili/meilisearch");
        assertThat(new MeiliSearchContainer(mirror).getConfiguredImage().asCanonicalNameString())
                .isEqualTo("registry.example.internal/mirror/meili:v1.49.0");
    }
}
