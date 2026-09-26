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
 * M1 exit-gate end-to-end IT on a real server (v1.49.0): create index → settings
 * projection → write → wait-task → read/search/filter/sort/facet/pagination/nested filter
 * → fetch (with sort) → delete → index lifecycle, covering Chinese text and Long precision.
 * Uses the real {@link SdkMeiliRawGateway} (both the SDK channel and the own HTTP channel
 * are exercised).
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
    @DisplayName("Full lifecycle: create index (with projection) → batch write → read/search/filter/sort/facet/fetch → delete")
    void fullCrudSearchLifecycle() {
        var ops = newOps(true, Duration.ofSeconds(20));

        int taskUid = ops.createIndex(Book.class);
        ops.awaitTask(taskUid); // async task: wait explicitly for the terminal state (index-level write-then-read semantics)
        assertThat(ops.getTask(taskUid).status()).isEqualTo(MeiliTaskStatus.SUCCEEDED);
        assertThat(ops.indexExists(Book.class)).isTrue();

        // the settings projection really reached the server (verified by reading it back via the raw settings channel fixed by spikeA)
        String settings = ops.projectedSettings(Book.class).toJson();
        assertThat(settings).contains("searchableAttributes").contains("book_title");

        ops.saveAll(FIXTURE);

        // read back by primary key: Long bit-exact + nested/Chinese/date/array
        assertThat(ops.findById(9007199254740993L, Book.class)).hasValueSatisfying(b -> {
            assertThat(b.id()).isEqualTo(9007199254740993L);
            assertThat(b.title()).isEqualTo("三体");
            assertThat(b.author().city()).isEqualTo("北京");
            assertThat(b.tags()).containsExactly("雨果奖", "长篇");
            assertThat(b.publishedAt()).isEqualTo(OffsetDateTime.parse("2008-01-01T00:00:00Z"));
        });

        // missing document → empty Optional, not an exception
        assertThat(ops.findById(999L, Book.class)).isEmpty();

        assertThat(ops.count(Book.class)).isEqualTo(4L);

        // search: q + filter + sort + facet + pagination (page mode)
        MeiliSearchResult<Book> r = ops.search(MeiliQuery.query("科幻")
                .filter("price < 60").sort("price:asc")
                .facets("genre").page(1).hitsPerPage(10), Book.class);
        assertThat(r.getHits()).extracting(Book::title)
                .containsExactly("银河帝国：基地", "三体");
        assertThat(r.getPage()).isEqualTo(1);
        assertThat(r.getTotalHits()).isEqualTo(2L);
        assertThat(r.getFacetDistribution()).containsKey("genre");
        // facet counts scope = the result set after filtering (Dune at price 69 is already excluded by the filter)
        assertThat(r.getFacetDistribution().get("genre")).containsEntry("科幻", 2);

        // nested dotted-path filter
        assertThat(ops.search(MeiliQuery.query(null)
                .filter("author.city = \"纽约\""), Book.class)
                .getHits()).extracting(Book::title)
                .containsExactlyInAnyOrder("沙丘", "银河帝国：基地");

        // filterAdd accumulates with AND
        assertThat(ops.search(MeiliQuery.query("沙丘")
                .filterAdd("genre = \"科幻\"").filterAdd("price > 100"), Book.class)
                .getHits()).isEmpty();
        assertThat(ops.search(MeiliQuery.query("沙丘")
                .filterAdd("genre = \"科幻\"").filterAdd("price > 60"), Book.class)
                .getHits()).hasSize(1);

        // findAll: documents/fetch channel, filter + sort cross-checked against the real server (direct HTTP channel)
        List<Book> fetched = ops.findAll(Book.class, DocumentsFetchQuery.fetchQuery()
                .filter("genre = \"科幻\"").sort("price:desc").limit(2));
        assertThat(fetched).extracting(Book::title)
                .containsExactly("沙丘", "三体");

        // single write + callback chain + delete
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

        // deleteAll keeps the index; count drops to zero
        ops.deleteAll(Book.class);
        assertThat(ops.count(Book.class)).isZero();
        assertThat(ops.indexExists(Book.class)).isTrue();

        ops.deleteIndex(Book.class);
        assertThat(ops.indexExists(Book.class)).isFalse();
    }

    @Test
    @DisplayName("Server error on the direct HTTP channel: missing index → IndexAccessException with a readable error code")
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
    @DisplayName("Read callback chain takes effect on the real-server path (AfterLoad rewrites the raw document)")
    void afterLoadRewritesRawDocumentEndToEnd() {
        var opsPrep = newOps(true, Duration.ofSeconds(20));
        opsPrep.createIndex(Book.class);
        opsPrep.save(FIXTURE.get(1)); // the "To Live" fixture
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Book.class, (AfterLoadCallback<Book>) (json, i) ->
                json.replace("余华", "Yu Hua"));
        var ops = newOps(true, Duration.ofSeconds(20), cbs);
        assertThat(ops.findById(2L, Book.class)).hasValueSatisfying(b ->
                assertThat(b.author().name()).isEqualTo("Yu Hua"));
        ops.deleteIndex(Book.class);
    }
}
