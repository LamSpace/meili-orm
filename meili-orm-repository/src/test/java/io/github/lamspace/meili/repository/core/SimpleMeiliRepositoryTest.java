/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * L1: the delegation contract of {@link SimpleMeiliRepository} onto
 * {@link MeiliSearchOperations} — routing targets, per-item counting on batch deletes, the
 * fetch-ceiling WARN, Pageable conversion and estimated-total semantics.
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

    /** Nested type, verifies dotted-path bridging of Pageable sort properties. */
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
                        assertThat(e.getFormattedMessage()).contains("maxTotalHits").contains("truncated");
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
        // estimatedTotalHits=100 dodges PageImpl's page-coverage clamp (offset+pageSize must stay ≤ total to pass through)
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
    @DisplayName("Pageable sort properties bridge through projection names: author.city → author.city, renamed properties land on their projection name")
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
    @DisplayName("Unknown Pageable sort property: the query throws a locatable error right away")
    void pageableUnknownPropertyRejected() {
        assertThatThrownBy(() -> repo.findAll(PageRequest.of(0, 10, Sort.by("nope"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
        verify(ops, never()).search(any(MeiliQuery.class), any());
    }

    @Test
    @DisplayName("Response without a total falls back to the hit count as Page total (logged at DEBUG, never faked as exact)")
    void missingTotalsFallBackToHitsSize() {
        String raw = "{\"hits\":[{\"id\":1,\"book_title\":\"x\",\"genre\":\"g\",\"price\":1.0}]}";
        when(ops.search(any(MeiliQuery.class), org.mockito.ArgumentMatchers.eq(Book.class)))
                .thenReturn(MeiliSearchResult.from(raw, Book.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())));
        Page<Book> page = repo.findAll(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("PageImpl's page-coverage clamp is inherent commons semantics: an estimated total below offset+page content must be raised to cover the current page")
    void pageImplClampsTotalToCoverCurrentPage() {
        String raw = "{\"hits\":[{\"id\":1,\"book_title\":\"x\",\"genre\":\"g\",\"price\":1.0}],"
                + "\"estimatedTotalHits\":45,\"page\":3,\"hitsPerPage\":20}";
        when(ops.search(any(MeiliQuery.class), org.mockito.ArgumentMatchers.eq(Book.class)))
                .thenReturn(MeiliSearchResult.from(raw, Book.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())));
        Page<Book> page = repo.findAll(PageRequest.of(2, 20));
        assertThat(page.getTotalElements()).isEqualTo(41); // 40 + 1 hit, not an implementation defect
    }

    @Test
    void operationsExceptionsPropagateWithoutRewrapping() {
        when(ops.count(Book.class)).thenThrow(new MeiliOrmException("boom"));
        assertThatThrownBy(() -> repo.count())
                .isInstanceOf(MeiliOrmException.class)
                .hasMessage("boom");
    }
}
