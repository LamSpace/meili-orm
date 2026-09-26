package io.github.lamspace.meili.sc;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 矩阵服务连接 IT 的最小应用宿主：仅开启自动配置，连接信息完全交给服务连接桥接。
 *
 * <p>不用 {@code @SpringBootApplication}：避免组件扫描把同包另一退避宿主也拉进上下文。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class ServiceConnectionApp {
}
