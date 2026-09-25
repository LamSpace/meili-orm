package io.github.lamspace.meili.it;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 矩阵用例的 Boot 应用壳。
 *
 * <p>包即自动配置根：meili 自动配置经 imports 文件装配，实体扫描以本包为起点发现
 * {@link ITBook}。不声明任何业务 bean——矩阵验证的正是"零装配即可用"的 starter 语义。
 */
@SpringBootApplication
public class ItApp {
}
