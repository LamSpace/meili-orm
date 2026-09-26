package io.github.lamspace.meili.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.MeiliContainer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 双代矩阵仓库用例：commons 4.0.3（Boot 4 代）运行时下，starter 装配 + repository 模块
 * 兜底自动配置 + 派生/全文/分页/注解查询全链路真机往返，并与 Boot 3 侧保持逐条一致。
 *
 * <p>与 {@code it-boot3} 同名文件除两个哨兵期望值外逐字节一致（矩阵源码双份复制规则）。
 */
@SpringBootTest(classes = ItApp.class)
class MeiliRepositoryMatrixIT {

    /** Boot 代哨兵期望（本模块为 4）。 */
    private static final String EXPECTED_BOOT_MAJOR = "4.";

    /** commons 代结构哨兵期望：4.0 独有类 org.springframework.data.core.PropertyPath 存在为 true。 */
    private static final boolean EXPECT_GENERATION_4 = true;

    @DynamicPropertySource
    static void meiliProps(DynamicPropertyRegistry registry) {
        registry.add("meili.url", MeiliContainer::url);
        registry.add("meili.api-key", () -> MeiliContainer.MASTER_KEY);
        registry.add("meili.wait-task", () -> "true");
        registry.add("meili.index.auto-init", () -> "sync-settings");
        registry.add("meili.index.on-settings-drift", () -> "apply");
    }

    @Autowired
    MatrixBookRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new MatrixBook(9007199254740993L, "三体", "科幻", 59.0),
                new MatrixBook(2L, "沙丘", "科幻", 45.0),
                new MatrixBook(1L, "活着", "现实", 28.0)));
    }

    @Test
    void generationSentinels() {
        assertThat(SpringBootVersion.getVersion()).startsWith(EXPECTED_BOOT_MAJOR);
        boolean gen4;
        try {
            Class.forName("org.springframework.data.core.PropertyPath");
            gen4 = true;
        } catch (ClassNotFoundException e) {
            gen4 = false;
        }
        assertThat(gen4)
                .as("commons 钉定漂移：org.springframework.data.core.PropertyPath 存在性=%s，期望=%s",
                        gen4, EXPECT_GENERATION_4)
                .isEqualTo(EXPECT_GENERATION_4);
    }

    @Test
    void autoConfigurationRegistersRepositoryWithoutEnableAnnotation() {
        assertThat(repository).isNotNull();
    }

    @Test
    void derivedQueriesRoundTripIdentically() {
        assertThat(repository.findByGenre("科幻")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L);
        assertThat(repository.findByTitleContaining("三体")).extracting(b -> b.id)
                .containsExactly(9007199254740993L);
        Page<MatrixBook> page = repository.findPageByGenreOrderByPriceAsc("科幻",
                PageRequest.of(0, 1));
        assertThat(page.getContent()).extracting(b -> b.price).containsExactly(45.0);
        assertThat(page.getTotalElements()).isPositive();
        assertThat(repository.expensive(30.0)).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L);
        assertThat(repository.findById(9007199254740993L))
                .hasValueSatisfying(b -> assertThat(b.title).isEqualTo("三体"));
    }
}
