package io.github.lamspace.meili.testcontainers;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 桥接 IT 的最小应用宿主：仅开启自动配置，连接信息完全交给服务连接桥接提供。
 *
 * <p>刻意不用 {@code @SpringBootApplication}：它会带组件扫描，本包内还有退避场景的
 * 另一宿主类与实体，宿主之间不能被互相扫到。{@code @SpringBootConfiguration} 本身
 * 不是组件注解，同包共存安全；自动配置包（实体扫描基）由本类所在包界定。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class BridgeApp {
}
