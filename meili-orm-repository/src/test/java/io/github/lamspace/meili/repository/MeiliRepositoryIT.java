package io.github.lamspace.meili.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.AbstractMeiliIntegrationTest;
import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.internal.SdkMeiliRawGateway;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.DefaultMeiliSearchOperations;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.repository.core.MeiliRepositoryProxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * L3 真机（钉 v1.49.0）：仓库 + 派生查询全链路——索引创建（角色 settings 推送）、写入、
 * 各关键字命中集合与手写 Operations 查询等价、分页换算、Containing 中文全文、
 * NOT 对存在属性的行为钉死。任务矩阵扩面（双代）复用同一测试源。
 */
class MeiliRepositoryIT extends AbstractMeiliIntegrationTest {

    @MeiliDocument(indexName = "it_repo_books")
    public static class RepoBook {
        /** 主键。 */
        @MeiliId
        public Long id;
        /** 改名 + searchable。 */
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
        public String title;
        /** filterable。 */
        @MeiliField(filterable = true)
        public String genre;
        /** filterable + sortable。 */
        @MeiliField(filterable = true, sortable = true)
        public Double price;
        /** filterable 集合叶。 */
        @MeiliField(filterable = true)
        public List<String> tags;
        /** filterable 布尔。 */
        @MeiliField(filterable = true)
        public Boolean active;
        /** 嵌套。 */
        public RepoAuthor author;
    }

    /** 嵌套类型。 */
    public static class RepoAuthor {
        /** filterable+sortable。 */
        @MeiliField(filterable = true, sortable = true)
        public String city;
    }

    /** IT 仓库接口：覆盖主要关键字族。 */
    public interface RepoBookRepository extends MeiliRepository<RepoBook, Long> {
        List<RepoBook> findByGenre(String g);

        List<RepoBook> findByPriceGreaterThan(Double p);

        List<RepoBook> findByGenreOrderByPriceDesc(String g);

        List<RepoBook> findByTitleContaining(String t);

        List<RepoBook> findByAuthorCity(String c);

        List<RepoBook> findByTagsIn(List<String> tags);

        List<RepoBook> findByNotGenre(String g);

        List<RepoBook> findTop2ByGenreOrderByPriceAsc(String g);

        List<RepoBook> findByGenre(String g, org.springframework.data.domain.Pageable pg);

        Optional<RepoBook> findFirstByGenreOrderByPriceAsc(String g);

        Page<RepoBook> findPageByGenre(String g, org.springframework.data.domain.Pageable pg);
    }

    static RepoBookRepository repo;
    static MeiliSearchOperations ops;

    @BeforeAll
    static void wire() {
        Client client = client();
        Config config = new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY);
        ops = new DefaultMeiliSearchOperations(new SdkMeiliRawGateway(client, config),
                new MeiliMappingContext(), new Jackson2DocumentSerializer(new ObjectMapper()),
                MeiliEntityCallbacks.none(), true, Duration.ofSeconds(20));
        repo = (RepoBookRepository) MeiliRepositoryProxy.create(RepoBookRepository.class, ops,
                new MeiliMappingContext());
    }

    private static RepoBook book(long id, String title, String genre, double price,
                                 List<String> tags, boolean active, String city) {
        RepoBook b = new RepoBook();
        b.id = id;
        b.title = title;
        b.genre = genre;
        b.price = price;
        b.tags = tags;
        b.active = active;
        RepoAuthor a = new RepoAuthor();
        a.city = city;
        b.author = a;
        return b;
    }

    @Test
    void derivedQueriesAgainstRealServer() {
        if (ops.indexExists(RepoBook.class)) {
            ops.deleteIndex(RepoBook.class);
        }
        ops.createIndex(RepoBook.class);
        ops.saveAll(List.of(
                book(9007199254740993L, "三体", "科幻", 59.0, List.of("雨果奖", "长篇"), true, "北京"),
                book(1L, "活着", "现实", 28.0, List.of("经典"), true, "杭州"),
                book(2L, "沙丘", "科幻", 45.0, List.of("史诗"), false, "北京"),
                book(3L, "银河帝国", "科幻", 39.0, List.of("基地"), true, "上海"),
                book(4L, "平凡的世界", "现实", 33.0, List.of("茅盾"), true, "西安")));

        // 等值
        assertThat(repo.findByGenre("科幻")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L, 3L);
        // 比较 + 排序
        assertThat(repo.findByPriceGreaterThan(40.0)).extracting(b -> b.price)
                .containsExactlyInAnyOrder(59.0, 45.0);
        assertThat(repo.findByGenreOrderByPriceDesc("科幻")).extracting(b -> b.price)
                .containsExactly(59.0, 45.0, 39.0);
        // 全文 Containing（改名属性 book_title）
        assertThat(repo.findByTitleContaining("三体")).extracting(b -> b.id)
                .containsExactly(9007199254740993L);
        // 嵌套点路径 filterable
        assertThat(repo.findByAuthorCity("北京")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L);
        // IN + 布尔 NOT（服务端语义：NOT 命中显式 false 的文档）
        assertThat(repo.findByTagsIn(List.of("经典", "基地"))).extracting(b -> b.id)
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(repo.findByNotGenre("科幻")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(1L, 4L);
        // TopN
        assertThat(repo.findTop2ByGenreOrderByPriceAsc("科幻")).extracting(b -> b.price)
                .containsExactly(39.0, 45.0);
        // 分页换算：page 1(size 2) 第三小的科幻按价升序 = 59.0 单条
        List<RepoBook> paged = repo.findByGenre("科幻", PageRequest.of(1, 2, Sort.by("price")));
        assertThat(paged).extracting(b -> b.price).containsExactly(59.0);
        // Optional 首条
        assertThat(repo.findFirstByGenreOrderByPriceAsc("科幻")).hasValueSatisfying(
                b -> assertThat(b.price).isEqualTo(39.0));
        // Page 估算总数
        Page<RepoBook> page = repo.findPageByGenre("科幻", PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isPositive();
        // CRUD 面
        assertThat(repo.findById(9007199254740993L)).hasValueSatisfying(
                b -> assertThat(b.title).isEqualTo("三体"));
        assertThat(repo.count()).isEqualTo(5L);
        repo.deleteById(9007199254740993L);
        assertThat(repo.findById(9007199254740993L)).isEmpty();

        ops.deleteIndex(RepoBook.class);
    }
}
