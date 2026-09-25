package io.github.lamspace.meili.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boot 3.5.16 演示启动壳。
 *
 * <p>本包即自动配置根：实体扫描自此处出发发现 example-common 的 {@code domain} 子包，
 * 组件扫描发现 {@code web}/{@code config} 子包。壳内零业务代码——双 demo 的
 * 差异只有 BOM 与这一份启动类。
 */
@SpringBootApplication
public class MeiliExampleApplication {

    /**
     * 启动类由框架构造；入口逻辑全部在 {@link #main(String[])}。
     */
    public MeiliExampleApplication() {
    }

    /**
     * 进程入口。
     *
     * @param args 透传给 Spring Boot 的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MeiliExampleApplication.class, args);
    }
}
