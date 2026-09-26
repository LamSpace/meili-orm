package io.github.lamspace.meili.testcontainers.manual;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 手工桥接样例的应用宿主：独立子包，令默认发现路径可解析出唯一
 * {@code @SpringBootConfiguration}（文档样例即依赖该发现路径）。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class ManualBridgeApp {
}
