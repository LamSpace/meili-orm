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
 * L3 against the real server (pinned v1.49.0): the full repository + derived-query chain —
 * index creation (role settings push), writes, per-keyword hit sets equivalent to hand-written
 * Operations queries, page conversion, Containing on Chinese full text, and the NOT behavior on
 * declared properties pinned. The two-generation matrix expansion reuses this same test source.
 */
class MeiliRepositoryIT extends AbstractMeiliIntegrationTest {

    @MeiliDocument(indexName = "it_repo_books")
    public static class RepoBook {
        /** Primary key. */
        @MeiliId
        public Long id;
        /** Renamed + searchable. */
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
        public String title;
        /** filterable. */
        @MeiliField(filterable = true)
        public String genre;
        /** filterable + sortable. */
        @MeiliField(filterable = true, sortable = true)
        public Double price;
        /** filterable collection leaf. */
        @MeiliField(filterable = true)
        public List<String> tags;
        /** filterable boolean. */
        @MeiliField(filterable = true)
        public Boolean active;
        /** Nested. */
        public RepoAuthor author;
    }

    /** Nested type. */
    public static class RepoAuthor {
        /** filterable+sortable. */
        @MeiliField(filterable = true, sortable = true)
        public String city;
    }

    /** IT repository interface: covers the main keyword families. */
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

        // equality
        assertThat(repo.findByGenre("科幻")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L, 3L);
        // comparison + sorting
        assertThat(repo.findByPriceGreaterThan(40.0)).extracting(b -> b.price)
                .containsExactlyInAnyOrder(59.0, 45.0);
        assertThat(repo.findByGenreOrderByPriceDesc("科幻")).extracting(b -> b.price)
                .containsExactly(59.0, 45.0, 39.0);
        // full-text Containing (renamed property book_title)
        assertThat(repo.findByTitleContaining("三体")).extracting(b -> b.id)
                .containsExactly(9007199254740993L);
        // nested dotted path filterable
        assertThat(repo.findByAuthorCity("北京")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(9007199254740993L, 2L);
        // IN + boolean NOT (server semantics: NOT matches documents with explicit false)
        assertThat(repo.findByTagsIn(List.of("经典", "基地"))).extracting(b -> b.id)
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(repo.findByNotGenre("科幻")).extracting(b -> b.id)
                .containsExactlyInAnyOrder(1L, 4L);
        // TopN
        assertThat(repo.findTop2ByGenreOrderByPriceAsc("科幻")).extracting(b -> b.price)
                .containsExactly(39.0, 45.0);
        // page conversion: page 1 (size 2) of the genre-filtered docs sorted by price asc = the single 59.0 hit (third cheapest)
        List<RepoBook> paged = repo.findByGenre("科幻", PageRequest.of(1, 2, Sort.by("price")));
        assertThat(paged).extracting(b -> b.price).containsExactly(59.0);
        // Optional first hit
        assertThat(repo.findFirstByGenreOrderByPriceAsc("科幻")).hasValueSatisfying(
                b -> assertThat(b.price).isEqualTo(39.0));
        // Page estimated total
        Page<RepoBook> page = repo.findPageByGenre("科幻", PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isPositive();
        // CRUD surface
        assertThat(repo.findById(9007199254740993L)).hasValueSatisfying(
                b -> assertThat(b.title).isEqualTo("三体"));
        assertThat(repo.count()).isEqualTo(5L);
        repo.deleteById(9007199254740993L);
        assertThat(repo.findById(9007199254740993L)).isEmpty();

        ops.deleteIndex(RepoBook.class);
    }
}
