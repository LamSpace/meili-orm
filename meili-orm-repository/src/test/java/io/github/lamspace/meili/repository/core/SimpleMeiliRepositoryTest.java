package io.github.lamspace.meili.repository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * L1：{@link SimpleMeiliRepository} 对 {@link MeiliSearchOperations} 的委托契约——
 * 路由落点、批量删除逐条计数、fetch 上限 WARN、Pageable 换算与估算总数语义。
 */
class SimpleMeiliRepositoryTest {

    @MeiliDocument(indexName = "books")
    static class Book {
        @MeiliId
        Long id;
        @MeiliField(name = "book_title", searchable = true)
        String title;
        @MeiliField(filterable = true)
        String genre;
        @MeiliField(filterable = true, sortable = true)
        Double price;

        Book() {
        }

        Book(Long id, String title, String genre, Double price) {
            this.id = id;
            this.title = title;
            this.genre = genre;
            this.price = price;
        }
    }

    /** 嵌套类型，验证 Pageable sort 的点路径桥接。 */
    static class Author {
        @MeiliField(filterable = true, sortable = true)
        String city;
    }

    @MeiliDocument(indexName = "bnested")
    static class BookWithAuthor {
        @MeiliId
        Long id;
        Author author;
    }

    MeiliSearchOperations ops;
    SimpleMeiliRepository<Book, Long> repo;

    @BeforeEach
    void setUp() {
        ops = mock(MeiliSearchOperations.class);
        repo = new SimpleMeiliRepository<>(ops, MeiliPersistentEntity.of(Book.class));
    }

    @Test
    void saveAndSaveAllDelegateToUpsertPath() {
        Book b = new Book(1L, "三体", "科幻", 59.0);
        when(ops.save(b)).thenReturn(b);
        assertThat(repo.save(b)).isSameAs(b);
        List<Book> batch = List.of(b);
        when(ops.saveAll(batch)).thenReturn(batch);
        assertThat(repo.saveAll(batch)).isSameAs(batch);
    }

    @Test
    void findByIdAndExistsRouteToDocumentChannel() {
        Book b = new Book(1L, "x", "g", 1.0);
        when(ops.findById(1L, Book.class)).thenReturn(Optional.of(b));
        when(ops.findById(2L, Book.class)).thenReturn(Optional.empty());
        assertThat(repo.findById(1L)).contains(b);
        assertThat(repo.existsById(1L)).isTrue();
        assertThat(repo.existsById(2L)).isFalse();
    }

    @Test
    void countGoesThroughStatsBackedOperations() {
        when(ops.count(Book.class)).thenReturn(7L);
        assertThat(repo.count()).isEqualTo(7L);
    }

    @Test
    void deleteEntityUsesMetamodelId() {
        Book b = new Book(42L, "x", "g", 1.0);
        repo.delete(b);
        verify(ops).deleteById(42L, Book.class);
    }

    @Test
    void deleteAllIterableIssuesOneRequestPerEntity() {
        List<Book> books = List.of(new Book(1L, "a", "g", 1.0), new Book(2L, "b", "g", 2.0));
        repo.deleteAll(books);
        verify(ops).deleteById(1L, Book.class);
        verify(ops).deleteById(2L, Book.class);
    }

    @Test
    void deleteAllByIdLoopsSingleDeletes() {
        repo.deleteAllById(List.of(1L, 2L, 3L));
        verify(ops, times(3)).deleteById(any(), org.mockito.ArgumentMatchers.eq(Book.class));
    }

    @Test
    void deleteAllClearsIndexDocuments() {
        repo.deleteAll();
        verify(ops).deleteAll(Book.class);
    }

    @Test
    void findAllByIdLoopsFindById() {
        when(ops.findById(1L, Book.class)).thenReturn(Optional.of(new Book(1L, "a", "g", 1.0)));
        when(ops.findById(9L, Book.class)).thenReturn(Optional.empty());
        assertThat(repo.findAllById(List.of(1L, 9L))).hasSize(1);
    }

    @Test
    void findAllAtCeilingWarnsAboutTruncation() {
        List<Book> big = new ArrayList<>();
        for (int i = 0; i < SimpleMeiliRepository.FETCH_CEILING; i++) {
            big.add(new Book((long) i, "t", "g", 1.0));
        }
        when(ops.findAll(org.mockito.ArgumentMatchers.eq(Book.class), any(DocumentsFetchQuery.class)))
                .thenReturn(big);
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SimpleMeiliRepository.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(repo.findAll()).hasSize(SimpleMeiliRepository.FETCH_CEILING);
            assertThat(appender.list)
                    .anySatisfy(e -> {
                        assertThat(e.getLevel()).isEqualTo(Level.WARN);
                        assertThat(e.getFormattedMessage()).contains("maxTotalHits").contains("截断");
                    });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void findAllBelowCeilingDoesNotWarn() {
        when(ops.findAll(org.mockito.ArgumentMatchers.eq(Book.class), any(DocumentsFetchQuery.class)))
                .thenReturn(List.of(new Book(1L, "a", "g", 1.0)));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SimpleMeiliRepository.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            repo.findAll();
            assertThat(appender.list).noneSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void pageableBrowseTranslatesToPageModeWithBridgedSort() {
        // estimatedTotalHits=100 避开 PageImpl 的页覆盖钳制（offset+pageSize 必须 ≤ total 才透传）
        String raw = "{\"hits\":[{\"id\":1,\"book_title\":\"三体\",\"genre\":\"科幻\",\"price\":59.0}],"
                + "\"estimatedTotalHits\":100,\"page\":3,\"hitsPerPage\":20,\"totalPages\":5}";
        when(ops.search(any(MeiliQuery.class), org.mockito.ArgumentMatchers.eq(Book.class)))
                .thenReturn(MeiliSearchResult.from(raw, Book.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())));
        Page<Book> page = repo.findAll(PageRequest.of(2, 20, Sort.by("price").descending()));
        ArgumentCaptor<MeiliQuery> cap = ArgumentCaptor.forClass(MeiliQuery.class);
        verify(ops).search(cap.capture(), org.mockito.ArgumentMatchers.eq(Book.class));
        MeiliQuery q = cap.getValue();
        assertThat(q.getPage()).isEqualTo(3);          // 0-based → 1-based
        assertThat(q.getHitsPerPage()).isEqualTo(20);
        assertThat(q.getSort()).containsExactly("price:desc");
        assertThat(page.getTotalElements()).isEqualTo(100);   // estimated passthrough
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).title).isEqualTo("三体");
    }

    @Test
    @DisplayName("Pageable sort 属性经投影名桥：author.city → author.city，改名属性落投影名")
    void pageableSortBridgesNestedAndRenamedPaths() {
        SimpleMeiliRepository<BookWithAuthor, Long> nested = new SimpleMeiliRepository<>(
                ops, MeiliPersistentEntity.of(BookWithAuthor.class));
        String raw = "{\"hits\":[]}";
        when(ops.search(any(MeiliQuery.class), org.mockito.ArgumentMatchers.eq(BookWithAuthor.class)))
                .thenReturn(MeiliSearchResult.from(raw, BookWithAuthor.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())));
        nested.findAll(PageRequest.of(0, 10, Sort.by("author.city")));
        ArgumentCaptor<MeiliQuery> cap = ArgumentCaptor.forClass(MeiliQuery.class);
        verify(ops).search(cap.capture(), org.mockito.ArgumentMatchers.eq(BookWithAuthor.class));
        assertThat(cap.getValue().getSort()).containsExactly("author.city:asc");
    }

    @Test
    @DisplayName("未知 Pageable sort 属性：启动查询即抛可定位错误")
    void pageableUnknownPropertyRejected() {
        assertThatThrownBy(() -> repo.findAll(PageRequest.of(0, 10, Sort.by("nope"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
        verify(ops, never()).search(any(MeiliQuery.class), any());
    }

    @Test
    @DisplayName("响应无总数时以命中数兜底 Page 总数（DEBUG 记录，不伪装精确）")
    void missingTotalsFallBackToHitsSize() {
        String raw = "{\"hits\":[{\"id\":1,\"book_title\":\"x\",\"genre\":\"g\",\"price\":1.0}]}";
        when(ops.search(any(MeiliQuery.class), org.mockito.ArgumentMatchers.eq(Book.class)))
                .thenReturn(MeiliSearchResult.from(raw, Book.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())));
        Page<Book> page = repo.findAll(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("PageImpl 页覆盖钳制为 commons 固有语义：估算总数小于 offset+页内容时必须抬高以覆盖当前页")
    void pageImplClampsTotalToCoverCurrentPage() {
        String raw = "{\"hits\":[{\"id\":1,\"book_title\":\"x\",\"genre\":\"g\",\"price\":1.0}],"
                + "\"estimatedTotalHits\":45,\"page\":3,\"hitsPerPage\":20}";
        when(ops.search(any(MeiliQuery.class), org.mockito.ArgumentMatchers.eq(Book.class)))
                .thenReturn(MeiliSearchResult.from(raw, Book.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())));
        Page<Book> page = repo.findAll(PageRequest.of(2, 20));
        assertThat(page.getTotalElements()).isEqualTo(41); // 40 + 命中数 1，非实现缺陷
    }

    @Test
    void operationsExceptionsPropagateWithoutRewrapping() {
        when(ops.count(Book.class)).thenThrow(new MeiliOrmException("boom"));
        assertThatThrownBy(() -> repo.count())
                .isInstanceOf(MeiliOrmException.class)
                .hasMessage("boom");
    }
}
