package io.github.lamspace.meili.core.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.event.AfterLoadCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link DefaultMeiliSearchOperations} 搜索/索引段契约测试（网关全 mock）。 */
class DefaultMeiliSearchOperationsSearchTest {

    @MeiliDocument(indexName = "books")
    record Book(@MeiliId Long id,
                @MeiliField(searchable = true, searchableOrder = 1) String title,
                @MeiliField(filterable = true) String genre) {}

    @MeiliDocument(indexName = "bares")
    record Bare(@MeiliId Long id) {}

    MeiliRawGateway gw;
    DefaultMeiliSearchOperations ops;

    @BeforeEach
    void up() {
        gw = mock(MeiliRawGateway.class);
        ops = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(),
                new Jackson2DocumentSerializer(new ObjectMapper()),
                MeiliEntityCallbacks.none(), false, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("search 委托网关（IR 原样传递）并解析为强类型无损结果")
    void searchDelegatesIrAndParsesRaw() {
        when(gw.rawSearch(eq("books"), any(MeiliQuery.class)))
                .thenReturn("{\"hits\":[{\"id\":9007199254740993,\"title\":\"三体\"}],"
                        + "\"estimatedTotalHits\":1}");
        MeiliSearchResult<Book> r = ops.search(MeiliQuery.query("三体").filter("genre = \"科幻\""),
                Book.class);
        ArgumentCaptor<MeiliQuery> sent = ArgumentCaptor.forClass(MeiliQuery.class);
        verify(gw).rawSearch(eq("books"), sent.capture());
        assertThat(sent.getValue().getQ()).isEqualTo("三体");
        assertThat(sent.getValue().getFilterDsl()).isEqualTo("genre = \"科幻\"");
        assertThat(r.getHits()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo(9007199254740993L);
            assertThat(b.title()).isEqualTo("三体");
        });
    }

    @Test
    @DisplayName("search(q, type) 便捷入口等价于 IR 查询")
    void stringSearchOverload() {
        when(gw.rawSearch(eq("books"), any(MeiliQuery.class))).thenReturn("{\"hits\":[]}");
        ops.search("活着", Book.class);
        ArgumentCaptor<MeiliQuery> sent = ArgumentCaptor.forClass(MeiliQuery.class);
        verify(gw).rawSearch(eq("books"), sent.capture());
        assertThat(sent.getValue().getQ()).isEqualTo("活着");
    }

    @Test
    @DisplayName("搜索命中走同一条读回调链（AfterLoad 在反序列化前生效）")
    void searchAppliesReadChainToHits() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (AfterLoadCallback<Book>)
                (json, i) -> json.replace("沙丘", "沙丘！"));
        var ops2 = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(),
                new Jackson2DocumentSerializer(new ObjectMapper()), cbs, false,
                Duration.ofSeconds(5));
        when(gw.rawSearch(eq("books"), any(MeiliQuery.class)))
                .thenReturn("{\"hits\":[{\"id\":1,\"title\":\"沙丘\"}]}");
        assertThat(ops2.search("x", Book.class).getHits().get(0).title()).isEqualTo("沙丘！");
    }

    @Test
    @DisplayName("createIndex：主键名取元模型；投影非空则推 settings，返回最后一步 uid")
    void createIndexPushesSettingsWhenProjectedNotEmpty() {
        when(gw.createIndex("books", "id")).thenReturn(1);
        when(gw.updateSettings(eq("books"), any(String.class))).thenReturn(2);
        assertThat(ops.createIndex(Book.class)).isEqualTo(2);
        ArgumentCaptor<String> settings = ArgumentCaptor.forClass(String.class);
        verify(gw).updateSettings(eq("books"), settings.capture());
        assertThat(settings.getValue())
                .contains("\"searchableAttributes\"")
                .contains("\"title\"")
                .contains("\"filterableAttributes\"")
                .contains("\"genre\"");
    }

    @Test
    void createIndexSkipsSettingsWhenNothingDeclared() {
        when(gw.createIndex("bares", "id")).thenReturn(9);
        assertThat(ops.createIndex(Bare.class)).isEqualTo(9);
        verify(gw, never()).updateSettings(any(), any());
    }

    @Test
    @DisplayName("multiSearch：串行按序执行，结果按序返回")
    void multiSearchSerialInOrder() {
        when(gw.rawSearch(eq("books"), any(MeiliQuery.class)))
                .thenReturn("{\"hits\":[{\"id\":1,\"title\":\"a\"}]}", "{\"hits\":[]}");
        var results = ops.multiSearch(List.of(MeiliQuery.query("a"), MeiliQuery.query("b")),
                Book.class);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getHits()).hasSize(1);
        assertThat(results.get(1).getHits()).isEmpty();
        verify(gw, org.mockito.Mockito.times(2)).rawSearch(eq("books"), any());
    }

    @Test
    void indexLifecycleDelegates() {
        when(gw.indexExists("books")).thenReturn(true);
        assertThat(ops.indexExists(Book.class)).isTrue();
        when(gw.deleteIndex("books")).thenReturn(4);
        ops.deleteIndex(Book.class);
        verify(gw).deleteIndex("books");
    }

    @Test
    @DisplayName("applySettings 对无投影实体快速失败（不误发空 PATCH）")
    void applySettingsEmptyRejected() {
        assertThatThrownBy(() -> ops.applySettings(Bare.class))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("投影");
    }
}
