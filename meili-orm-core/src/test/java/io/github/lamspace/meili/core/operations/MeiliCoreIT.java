package io.github.lamspace.meili.core.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.core.AbstractMeiliIntegrationTest;
import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.event.AfterLoadCallback;
import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.core.internal.SdkMeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.task.MeiliTaskStatus;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * M1 出口真机全链路 IT（v1.49.0）：建索引→settings 投影→写→wait-task→读/搜/filter/
 * sort/facet/分页/嵌套 filter→fetch（含 sort）→删→索引生命周期，含中文与 Long 精度。
 * 使用真实 {@link SdkMeiliRawGateway}（SDK 通道 + 自有 HTTP 通道各覆盖）。
 */
class MeiliCoreIT extends AbstractMeiliIntegrationTest {

    @MeiliDocument(indexName = "m1core_books")
    record Book(@MeiliId Long id,
                @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
                @MeiliField(filterable = true, searchable = true) String genre,
                @MeiliField(sortable = true, filterable = true) Double price,
                Author author,
                List<String> tags,
                OffsetDateTime publishedAt) {}

    record Author(String name, @MeiliField(filterable = true) String city) {}

    private static final List<Book> FIXTURE = List.of(
            new Book(9007199254740993L, "三体", "科幻", 59.0,
                    new Author("刘慈欣", "北京"), List.of("雨果奖", "长篇"),
                    OffsetDateTime.parse("2008-01-01T00:00:00Z")),
            new Book(2L, "活着", "现实", 39.0,
                    new Author("余华", "杭州"), List.of("经典"),
                    OffsetDateTime.parse("1993-01-01T00:00:00Z")),
            new Book(3L, "沙丘", "科幻", 69.0,
                    new Author("弗兰克·赫伯特", "纽约"), List.of("雨果奖"),
                    OffsetDateTime.parse("1965-01-01T00:00:00Z")),
            new Book(4L, "银河帝国：基地", "科幻", 49.0,
                    new Author("阿西莫夫", "纽约"), List.of("经典", "基地"),
                    OffsetDateTime.parse("1951-01-01T00:00:00Z")));

    private static DefaultMeiliSearchOperations newOps(boolean waitTask, Duration timeout) {
        return newOps(waitTask, timeout, MeiliEntityCallbacks.none());
    }

    private static DefaultMeiliSearchOperations newOps(boolean waitTask, Duration timeout,
                                                       MeiliEntityCallbacks callbacks) {
        Client client = client();
        Config config = new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY);
        return new DefaultMeiliSearchOperations(
                new SdkMeiliRawGateway(client, config),
                new MeiliMappingContext(),
                new Jackson2DocumentSerializer(new ObjectMapper()),
                callbacks, waitTask, timeout);
    }

    @Test
    @DisplayName("全生命周期：建索引（含投影）→批量写→读/搜/filter/sort/facet/fetch→删")
    void fullCrudSearchLifecycle() {
        var ops = newOps(true, Duration.ofSeconds(20));

        int taskUid = ops.createIndex(Book.class);
        ops.awaitTask(taskUid); // 异步任务：显式等待到终态（写后可查语义的索引版）
        assertThat(ops.getTask(taskUid).status()).isEqualTo(MeiliTaskStatus.SUCCEEDED);
        assertThat(ops.indexExists(Book.class)).isTrue();

        // settings 投影确已推送到服务端（读原文验证，spikeA 定的 raw settings 通道）
        String settings = ops.projectedSettings(Book.class).toJson();
        assertThat(settings).contains("searchableAttributes").contains("book_title");

        ops.saveAll(FIXTURE);

        // 主键读回：Long 逐位无损 + 嵌套/中文/日期/数组
        assertThat(ops.findById(9007199254740993L, Book.class)).hasValueSatisfying(b -> {
            assertThat(b.id()).isEqualTo(9007199254740993L);
            assertThat(b.title()).isEqualTo("三体");
            assertThat(b.author().city()).isEqualTo("北京");
            assertThat(b.tags()).containsExactly("雨果奖", "长篇");
            assertThat(b.publishedAt()).isEqualTo(OffsetDateTime.parse("2008-01-01T00:00:00Z"));
        });

        // 缺失文档 → 空 Optional，不是异常
        assertThat(ops.findById(999L, Book.class)).isEmpty();

        assertThat(ops.count(Book.class)).isEqualTo(4L);

        // 搜索：q + filter + sort + facet + 分页（page 模式）
        MeiliSearchResult<Book> r = ops.search(MeiliQuery.query("科幻")
                .filter("price < 60").sort("price:asc")
                .facets("genre").page(1).hitsPerPage(10), Book.class);
        assertThat(r.getHits()).extracting(Book::title)
                .containsExactly("银河帝国：基地", "三体");
        assertThat(r.getPage()).isEqualTo(1);
        assertThat(r.getTotalHits()).isEqualTo(2L);
        assertThat(r.getFacetDistribution()).containsKey("genre");
        // facet 计数作用域 = filter 之后的结果集（沙丘 price 69 已被 filter 排除）
        assertThat(r.getFacetDistribution().get("genre")).containsEntry("科幻", 2);

        // 嵌套点路径 filter
        assertThat(ops.search(MeiliQuery.query(null)
                .filter("author.city = \"纽约\""), Book.class)
                .getHits()).extracting(Book::title)
                .containsExactlyInAnyOrder("沙丘", "银河帝国：基地");

        // filterAdd 累积 AND
        assertThat(ops.search(MeiliQuery.query("沙丘")
                .filterAdd("genre = \"科幻\"").filterAdd("price > 100"), Book.class)
                .getHits()).isEmpty();
        assertThat(ops.search(MeiliQuery.query("沙丘")
                .filterAdd("genre = \"科幻\"").filterAdd("price > 60"), Book.class)
                .getHits()).hasSize(1);

        // findAll：documents/fetch 通道，filter + sort 真机反查（HTTP 直连通道）
        List<Book> fetched = ops.findAll(Book.class, DocumentsFetchQuery.fetchQuery()
                .filter("genre = \"科幻\"").sort("price:desc").limit(2));
        assertThat(fetched).extracting(Book::title)
                .containsExactly("沙丘", "三体");

        // 单条写 + 回调链 + 删
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (BeforeConvertCallback<Book>) (e, i) ->
                new Book(e.id(), e.title().trim(), e.genre(), e.price(), e.author(), e.tags(),
                        e.publishedAt()));
        var opsCb = newOps(true, Duration.ofSeconds(20), cbs);
        Book saved = opsCb.save(new Book(5L, "  围城  ", "现实", 35.0,
                new Author("钱钟书", "上海"), List.of(), OffsetDateTime.parse("1947-01-01T00:00:00Z")));
        assertThat(saved.title()).isEqualTo("围城");
        assertThat(opsCb.findById(5L, Book.class)).hasValueSatisfying(b ->
                assertThat(b.title()).isEqualTo("围城"));
        opsCb.deleteById(5L, Book.class);
        assertThat(opsCb.findById(5L, Book.class)).isEmpty();

        // deleteAll 保索引，count 归零
        ops.deleteAll(Book.class);
        assertThat(ops.count(Book.class)).isZero();
        assertThat(ops.indexExists(Book.class)).isTrue();

        ops.deleteIndex(Book.class);
        assertThat(ops.indexExists(Book.class)).isFalse();
    }

    @Test
    @DisplayName("HTTP 直连通道的服务端错误：不存在索引 → IndexAccessException 且错误码可读")
    void missingIndexSurfacesErrorCodeFromHttpChannel() {
        var ops = newOps(false, Duration.ofSeconds(5));
        @MeiliDocument(indexName = "m1core_no_such_index")
        record Ghost(@MeiliId Long id) {}
        assertThatThrownBy(() -> ops.count(Ghost.class))
                .isInstanceOf(MeiliIndexAccessException.class)
                .satisfies(t -> assertThat(((MeiliIndexAccessException) t).getMeiliCode())
                        .isEqualTo("index_not_found"));
    }

    @Test
    @DisplayName("读回调链在真机路径生效（AfterLoad 改写 raw 文档）")
    void afterLoadRewritesRawDocumentEndToEnd() {
        var opsPrep = newOps(true, Duration.ofSeconds(20));
        opsPrep.createIndex(Book.class);
        opsPrep.save(FIXTURE.get(1)); // 活着
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (AfterLoadCallback<Book>) (json, i) ->
                json.replace("余华", "Yu Hua"));
        var ops = newOps(true, Duration.ofSeconds(20), cbs);
        assertThat(ops.findById(2L, Book.class)).hasValueSatisfying(b ->
                assertThat(b.author().name()).isEqualTo("Yu Hua"));
        ops.deleteIndex(Book.class);
    }
}
