package io.github.lamspace.meili.testcontainers.manual;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.testcontainers.MeiliSearchContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 真机 IT：文档"手工桥接"样例逐字可跑形态——默认发现解析应用配置，
 * 容器经嵌套 {@code @TestConfiguration} 接成 {@link MeiliConnectionDetails} bean，
 * 属性默认退避，保存后按主键读取往返成功。
 */
@SpringBootTest(properties = "meili.wait-task=true")
class ManualBridgeIT {

    /** 手工管理生命周期的容器：类加载即启动，Ryuk 负责回收。 */
    private static final MeiliSearchContainer MEILI = new MeiliSearchContainer();

    static {
        MEILI.start();
    }

    /** 装配链末端的数据操作面。 */
    @Autowired
    private MeiliSearchOperations operations;

    /**
     * 经手工桥接的装配链完成保存-读取往返。
     */
    @Test
    void saveThenReadByIdRoundTripsThroughManuallyBridgedContainer() {
        operations.save(new ManualBridgeBook(77L, "手工桥接"));

        assertThat(operations.findById(77L, ManualBridgeBook.class))
                .hasValueSatisfying(book -> assertThat(book.title()).isEqualTo("手工桥接"));
    }

    /**
     * 手工桥接配置：声明 {@link MeiliConnectionDetails} bean 即令属性默认实现退避，
     * 与文档样例逐字对应。
     */
    @TestConfiguration
    static class BridgeConfig {

        /**
         * 把静态容器的连接事实接成详情 bean。
         *
         * @return 委托活容器的连接详情
         */
        @Bean
        MeiliConnectionDetails meiliConnectionDetails() {
            return new MeiliConnectionDetails() {
                @Override
                public String getUrl() {
                    return MEILI.getUrl();
                }

                @Override
                public String getApiKey() {
                    return MEILI.getApiKey();
                }
            };
        }
    }
}
