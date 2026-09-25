package io.github.lamspace.meili.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import io.github.lamspace.meili.example.web.BookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 装配期防腐冒烟（零外部依赖）：auto-init=none + 未监听端口，验证的是"启动上下文
 * 不触网也能完成全量装配"——扫描器发现实体、控制器/回调/序列化器/模板 bean 齐备、
 * 默认序列化通道为 Jackson2（demo 未引入可选 Jackson3 模块）。
 *
 * <p>真机行为验证不在此层（由 demo README 的 curl 冒烟脚本承担），本测试只兜装配
 * 回归：任何 wiring 断裂在 mvn verify 即红，无需 Docker。
 */
@SpringBootTest(properties = {
        "meili.url=http://127.0.0.1:1",
        "meili.api-key=smoke-dummy-key",
        "meili.index.auto-init=none"
})
class ExampleContextSmokeTest {

    @Autowired
    private BookController controller;

    @Autowired
    private MeiliSearchOperations operations;

    @Autowired
    private MeiliEntityCallbacks callbacks;

    @Autowired
    private MeiliDocumentSerializer serializer;

    @Test
    void wiringCompletesWithoutServerContact() {
        assertThat(controller).isNotNull();
        assertThat(operations).isNotNull();
        assertThat(serializer).isInstanceOf(Jackson2DocumentSerializer.class);
        assertThat(callbacks.registeredCount()).isGreaterThanOrEqualTo(1);
    }
}
