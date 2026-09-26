package io.github.lamspace.meili.testcontainers;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 退避场景宿主：在桥接之外声明用户自有 {@link MeiliConnectionDetails} bean，
 * 用户 bean 的连接信息必须最终到达 Config。
 *
 * <p>索引初始化关闭：本场景不发起任何真机读写，只需装配结果可断言。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class BridgeBackoffApp {

    /** 用户 bean 的固定 URL，与服务容器地址明显不同，便于断言取舍。 */
    static final String USER_URL = "http://user-provided.example:1";

    /** 用户 bean 的固定密钥。 */
    static final String USER_KEY = "user-key";

    /**
     * 用户自有连接详情：按自动配置契约应使属性实现退避，并使服务连接桥接同样退避。
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
