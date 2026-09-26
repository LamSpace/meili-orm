package io.github.lamspace.meili.repository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lamspace.meili.core.event.AfterConvertCallback;
import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.DefaultMeiliSearchOperations;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.repository.MeiliRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * L1：仓库方法继承 Operations 的全部横切语义（spec"委托链路透明"场景）——
 * BeforeConvert 修改落在发出文档、AfterConvert 修改落在返回实体、wait-task 开关生效。
 */
class MeiliRepositoryLifecycleTest {

    @MeiliDocument(indexName = "cb_books")
    public static class Book {
        /** 主键。 */
        @MeiliId
        public Long id;
        /** 标题。 */
        public String title;

        Book() {
        }

        Book(Long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    /** 纯 CRUD 夹具仓库。 */
    public interface BookRepo extends MeiliRepository<Book, Long> {
    }

    private MeiliSearchOperations ops(boolean waitTask, MeiliEntityCallbacks cbs, MeiliRawGateway gw) {
        return new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(),
                new Jackson2DocumentSerializer(new ObjectMapper()), cbs,
                waitTask, Duration.ofSeconds(5));
    }

    @Test
    void writePathCallbacksAndWaitTaskPropagateThroughRepository() {
        MeiliRawGateway gw = mock(MeiliRawGateway.class);
        MeiliEntityCallbacks cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (BeforeConvertCallback<Book>) (e, idx) -> new Book(e.id, e.title + "!"));
        MeiliSearchOperations operations = ops(true, cbs, gw);
        BookRepo repo = (BookRepo) MeiliRepositoryProxy.create(BookRepo.class, operations,
                new MeiliMappingContext());
        when(gw.updateDocuments(eq("cb_books"), any())).thenReturn(7);

        repo.save(new Book(1L, "三体"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(gw).updateDocuments(eq("cb_books"), json.capture());
        assertThat(json.getValue()).contains("三体!");          // BeforeConvert 生效
        verify(gw).awaitTask(eq(7), eq(Duration.ofSeconds(5))); // wait-task=true 生效
    }

    @Test
    void waitTaskOffIssuesNoAwait() {
        MeiliRawGateway gw = mock(MeiliRawGateway.class);
        MeiliSearchOperations operations = ops(false, MeiliEntityCallbacks.none(), gw);
        BookRepo repo = (BookRepo) MeiliRepositoryProxy.create(BookRepo.class, operations,
                new MeiliMappingContext());
        when(gw.updateDocuments(eq("cb_books"), any())).thenReturn(3);

        repo.save(new Book(2L, "x"));
        verify(gw, never()).awaitTask(org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void readPathCallbacksPropagateThroughRepository() {
        MeiliRawGateway gw = mock(MeiliRawGateway.class);
        MeiliEntityCallbacks cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (AfterConvertCallback<Book>) (e, idx) -> {
            e.title = e.title + "(read)";
            return e;
        });
        MeiliSearchOperations operations = ops(false, cbs, gw);
        BookRepo repo = (BookRepo) MeiliRepositoryProxy.create(BookRepo.class, operations,
                new MeiliMappingContext());
        when(gw.fetchRawDocument("cb_books", "1"))
                .thenReturn(Optional.of("{\"id\":1,\"title\":\"三体\"}"));

        Book loaded = repo.findById(1L).orElseThrow();
        assertThat(loaded.title).isEqualTo("三体(read)");
    }
}
