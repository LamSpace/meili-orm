package io.github.lamspace.meili.sc;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 矩阵退避场景宿主：用户自有 {@link MeiliConnectionDetails} bean 与
 * {@code @ServiceConnection} 容器共存，装配结果必须是用户 bean 的连接信息。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class ServiceConnectionBackoffApp {

    /** 用户 bean 的固定 URL，与容器地址明显不同，便于断言取舍。 */
    static final String USER_URL = "http://user-provided.example:1";

    /** 用户 bean 的固定密钥。 */
    static final String USER_KEY = "user-key";

    /**
     * 用户自有连接详情：应使桥接退避（连同属性默认一并退避）。
     *
     * @return 固定连接信息的用户实现
     */
    @Bean
    MeiliConnectionDetails userMeiliConnectionDetails() {
        return new MeiliConnectionDetails() {
            @Override
            public String getUrl() {
                return USER_URL;
            }

            @Override
            public String getApiKey() {
                return USER_KEY;
            }
        };
    }
}
