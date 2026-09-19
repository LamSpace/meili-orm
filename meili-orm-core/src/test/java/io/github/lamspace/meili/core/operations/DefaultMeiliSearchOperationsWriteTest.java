package io.github.lamspace.meili.core.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.event.AfterConvertCallback;
import io.github.lamspace.meili.core.event.AfterLoadCallback;
import io.github.lamspace.meili.core.event.AfterSaveCallback;
import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link DefaultMeiliSearchOperations} 写读删语义契约测试（网关全 mock）。 */
class DefaultMeiliSearchOperationsWriteTest {

    static final ObjectMapper M = new ObjectMapper();
    static final Jackson2DocumentSerializer SER = new Jackson2DocumentSerializer(M);

    @MeiliDocument(indexName = "books")
    record Book(@MeiliId Long id, @MeiliField(name = "book_title") String title,
                @MeiliField(name = "price") Double price) {}

    MeiliRawGateway gw;
    DefaultMeiliSearchOperations ops;

    @BeforeEach
    void up() {
        gw = mock(MeiliRawGateway.class);
        ops = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                MeiliEntityCallbacks.none(), false, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("save = upsert 原始 JSON 单请求，主键与改名与元模型一致")
    void saveUpsertsViaRawJson() {
        when(gw.updateDocuments(eq("books"), anyString())).thenReturn(42);
        Book saved = ops.save(new Book(9007199254740993L, "三体", 59.0));
        verify(gw).updateDocuments("books",
                "{\"id\":9007199254740993,\"book_title\":\"三体\",\"price\":59.0}");
        assertThat(saved.title()).isEqualTo("三体");
    }

    @Test
    void saveNullIdFailsFastBeforeAnyCall() {
        assertThatThrownBy(() -> ops.save(new Book(null, "x", 1.0)))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("主键");
        verifyNoInteractions(gw);
    }

    @Test
    @DisplayName("saveAll：同类型多实体合并为单请求 JSON 数组")
    void saveAllSendsOneArrayRequest() {
        when(gw.updateDocuments(eq("books"), anyString())).thenReturn(7);
        List<Book> saved = ops.saveAll(List.of(
                new Book(1L, "a", 1.0), new Book(2L, "b", 2.0)));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gw).updateDocuments(eq("books"), body.capture());
        assertThat(body.getValue()).isEqualTo(
                "[{\"id\":1,\"book_title\":\"a\",\"price\":1.0},"
                        + "{\"id\":2,\"book_title\":\"b\",\"price\":2.0}]");
        assertThat(saved).hasSize(2);
    }

    @Test
    void emptySaveAllIsNoOp() {
        assertThat(ops.saveAll(List.of())).isEmpty();
        verifyNoInteractions(gw);
    }

    @Test
    void waitTaskTrueAwaitsReturnedUid() {
        var ops2 = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                MeiliEntityCallbacks.none(), true, Duration.ofSeconds(7));
        when(gw.updateDocuments(any(), any())).thenReturn(42);
        ops2.save(new Book(1L, "a", 1.0));
        verify(gw).awaitTask(42, Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("回调顺序：BeforeConvert 改变序列化内容，AfterSave 在其后触发")
    void callbacksFireInOrderOnSave() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (BeforeConvertCallback<Book>)
                (e, i) -> new Book(e.id(), e.title() + "!", e.price()));
        cbs.register(Book.class, (AfterSaveCallback<Book>) (e, i) -> {
            throw new IllegalStateException("after");
        });
        var ops3 = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                cbs, false, Duration.ofSeconds(5));
        when(gw.updateDocuments(any(), any())).thenReturn(1);
        assertThatThrownBy(() -> ops3.save(new Book(1L, "a", 1.0)))
                .isInstanceOf(IllegalStateException.class);
        verify(gw).updateDocuments("books", "{\"id\":1,\"book_title\":\"a!\",\"price\":1.0}");
    }

    @Test
    @DisplayName("findById：AfterLoad 改写先于反序列化，AfterConvert 其后")
    void findByIdRunsCallbackChainAndDeserializes() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (AfterLoadCallback<Book>)
                (json, i) -> json.replace("两体", "三体"));
        cbs.register(Book.class, (AfterConvertCallback<Book>)
                (e, i) -> new Book(e.id(), e.title() + "?", e.price()));
        var ops4 = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                cbs, false, Duration.ofSeconds(5));
        when(gw.fetchRawDocument("books", "1"))
                .thenReturn(Optional.of("{\"id\":1,\"book_title\":\"两体\"}"));
        Book b = ops4.findById(1L, Book.class).orElseThrow();
        assertThat(b.title()).isEqualTo("三体?");
    }

    @Test
    void missingDocumentEmptyOptional() {
        when(gw.fetchRawDocument(any(), any())).thenReturn(Optional.empty());
        assertThat(ops.findById(9L, Book.class)).isEmpty();
    }

    @Test
    void findByIdNullIdRejected() {
        assertThatThrownBy(() -> ops.findById(null, Book.class))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("主键");
        verifyNoInteractions(gw);
    }

    @Test
    @DisplayName("findAll：fetch 结果的每条 raw 文档走同一条读回调链")
    void findAllAppliesReadChainPerDocument() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (AfterConvertCallback<Book>)
                (e, i) -> new Book(e.id(), e.title() + "!", e.price()));
        var ops5 = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                cbs, false, Duration.ofSeconds(5));
        when(gw.fetchRawDocuments(eq("books"), any(DocumentsFetchQuery.class)))
                .thenReturn(List.of("{\"id\":1,\"book_title\":\"a\"}",
                        "{\"id\":2,\"book_title\":\"b\"}"));
        List<Book> all = ops5.findAll(Book.class, DocumentsFetchQuery.fetchQuery().limit(2));
        assertThat(all).extracting(Book::title).containsExactly("a!", "b!");
    }

    @Test
    void deleteByIdAwaitsWhenConfigured() {
        when(gw.deleteDocument("books", "1")).thenReturn(7);
        new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                MeiliEntityCallbacks.none(), true, Duration.ofSeconds(5))
                .deleteById(1L, Book.class);
        verify(gw).awaitTask(7, Duration.ofSeconds(5));
    }

    @Test
    void deleteAllDoesNotAwaitWhenNotConfigured() {
        when(gw.deleteAllDocuments("books")).thenReturn(3);
        ops.deleteAll(Book.class);
        verify(gw).deleteAllDocuments("books");
        verify(gw, never()).awaitTask(org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void countDelegatesIndexUid() {
        when(gw.count("books")).thenReturn(4L);
        assertThat(ops.count(Book.class)).isEqualTo(4L);
    }

    @Test
    void taskApiDelegatesWithConfiguredTimeout() {
        ops.awaitTask(5);
        verify(gw).awaitTask(5, Duration.ofSeconds(5));
    }
}
