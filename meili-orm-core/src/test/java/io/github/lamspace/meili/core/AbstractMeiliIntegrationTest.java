package io.github.lamspace.meili.core;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;

/**
 * MeiliSearch 真容器集成测试基类：子类经由 {@link #client()} 获得指向共享容器的
 * 已配置 SDK 客户端，无需自行拼装连接参数。
 *
 * <p>无状态、无线程约束；每次调用返回新的 {@code Client} 实例（构造零网络开销），
 * 指向同一容器。依赖 core 模块 test 作用域的 Testcontainers 与本机 Docker。</p>
 */
public abstract class AbstractMeiliIntegrationTest {

    /**
     * 构造指向共享容器的 SDK 客户端（默认 GsonJsonHandler 装配）。
     *
     * @return 以容器 URL 与测试 master key 配置的 {@link Client}
     */
    protected static Client client() {
        return new Client(new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY));
    }
}
