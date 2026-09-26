/**
 * meili-orm Testcontainers 集成：类型化 MeiliSearch 服务容器
 * （{@link io.github.lamspace.meili.testcontainers.MeiliSearchContainer}）与
 * Spring Boot 服务连接桥接
 * （{@link io.github.lamspace.meili.testcontainers.MeiliContainerConnectionDetailsFactory}）。
 *
 * <p>桥接仅产出 {@link io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails}，
 * 下游客户端与数据装配链完全复用自动配置行为。</p>
 *
 * <p>仅引用 Boot 3.5.x 与 4.x 两代稳定的服务连接工厂 SPI 与注解底座
 * （{@code org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory}、
 * {@code org.springframework.boot.testcontainers.service.connection.*}）；编译基线为最低支持代。</p>
 */
package io.github.lamspace.meili.testcontainers;
