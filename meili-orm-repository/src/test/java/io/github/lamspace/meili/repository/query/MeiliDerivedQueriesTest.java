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
package io.github.lamspace.meili.repository.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.core.MeiliRepositoryProxy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * L1 golden set (covering tasks 3.2–3.5): per-keyword MeiliQuery rendering, property
 * bridging, bootstrap role pre-checks, page/sort conversion, empty-IN short-circuit, and
 * locatable errors on the out-of-scope surface.
 */
class MeiliDerivedQueriesTest {

    @MeiliDocument(indexName = "dq_books")
    public static class DqBook {
        /** Primary key. */
        @MeiliId
        public Long id;
        /** Renamed + searchable. */
        @MeiliField(name = "book_title", searchable = true)
        public String title;
        /** searchable, not filterable. */
        @MeiliField(searchable = true)
        public String overview;
        /** filterable. */
        @MeiliField(filterable = true)
        public String genre;
        /** filterable+sortable. */
        @MeiliField(filterable = true, sortable = true)
        public Double price;
        /** filterable boolean. */
        @MeiliField(filterable = true)
        public Boolean active;
        /** sortable temporal. */
        @MeiliField(sortable = true)
        public java.time.OffsetDateTime publishedAt;
        /** filterable opaque collection leaf. */
        @MeiliField(filterable = true)
        public List<String> tags;
        /** Nested aggregate. */
        public DqAuthor author;
    }

    /** Nested type. */
    public static class DqAuthor {
        /** filterable+sortable. */
        @MeiliField(filterable = true, sortable = true)
        public String city;
        /** No roles declared. */
        public String name;
    }

    /** Happy-path repository. */
    public interface DqRepo extends MeiliRepository<DqBook, Long> {
        List<DqBook> findByGenre(String g);

        List<DqBook> findByPriceGreaterThan(Double p);

        List<DqBook> findByPriceBetween(Double a, Double b);

        List<DqBook> findByTagsIn(List<String> tags);

        List<DqBook> findByActiveTrue();

        List<DqBook> findByActiveFalseAndGenre(String g);

        List<DqBook> findByGenreNot(String g);

        List<DqBook> findByNotGenre(String g);

        List<DqBook> findByGenreAndPriceGreaterThan(String g, Double p);

        List<DqBook> findByGenreOrActive(String g, Boolean a);

        List<DqBook> findByTitleContaining(String t);

        List<DqBook> findByOverviewLike(String t);

        List<DqBook> findByAuthorCity(String c);

        List<DqBook> findTop3ByGenre(String g);

        List<DqBook> findByGenreOrderByPriceDesc(String g);

        List<DqBook> findByGenre(String g, Pageable pageable);

        Optional<DqBook> findFirstByGenre(String g);

        Page<DqBook> findPageByGenre(String g, Pageable pg);
    }

    MeiliSearchOperations ops;
    DqRepo repo;

    @BeforeEach
    void setUp() {
        ops = mock(MeiliSearchOperations.class);
        org.mockito.Mockito.doReturn(MeiliSearchResult.from("{\"hits\":[]}", DqBook.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())))
                .when(ops).search(any(MeiliQuery.class), any());
        repo = (DqRepo) MeiliRepositoryProxy.create(DqRepo.class, ops, new MeiliMappingContext());
    }

    private MeiliQuery captured() {
        ArgumentCaptor<MeiliQuery> cap = ArgumentCaptor.forClass(MeiliQuery.class);
        org.mockito.Mockito.verify(ops, org.mockito.Mockito.atLeastOnce()).search(cap.capture(), any());
        List<MeiliQuery> all = cap.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    void equalityRendersQuotedLiteral() {
        repo.findByGenre("科幻");
        assertThat(captured().getFilterDsl()).isEqualTo("genre = \"科幻\"");
    }

    @Test
    void comparisonAndRange() {
        repo.findByPriceGreaterThan(10.0);
        assertThat(captured().getFilterDsl()).isEqualTo("price > 10.0");
        repo.findByPriceBetween(1.0, 2.0);
        assertThat(captured().getFilterDsl()).isEqualTo("price BETWEEN 1.0 AND 2.0");
    }

    @Test
    void inRendersListAndEmptyShortCircuits() {
        repo.findByTagsIn(List.of("a", "b"));
        assertThat(captured().getFilterDsl()).isEqualTo("tags IN [\"a\", \"b\"]");
        repo.findByTagsIn(List.of());
        // empty-IN short-circuit: no second search is issued
        org.mockito.Mockito.verify(ops, org.mockito.Mockito.times(1)).search(any(MeiliQuery.class), any());
    }

    @Test
    void trueFalseAndMixedArity() {
        repo.findByActiveTrue();
        assertThat(captured().getFilterDsl()).isEqualTo("active = true");
        repo.findByActiveFalseAndGenre("g");
        assertThat(captured().getFilterDsl()).isEqualTo("active = false AND genre = \"g\"");
    }

    @Test
    void negationBothForms() {
        repo.findByGenreNot("x");
        assertThat(captured().getFilterDsl()).isEqualTo("genre != \"x\"");
        repo.findByNotGenre("x");
        assertThat(captured().getFilterDsl()).isEqualTo("NOT (genre = \"x\")");
    }

    @Test
    void andOrStructure() {
        repo.findByGenreAndPriceGreaterThan("科幻", 5.0);
        assertThat(captured().getFilterDsl()).isEqualTo("genre = \"科幻\" AND price > 5.0");
        repo.findByGenreOrActive("x", true);
        assertThat(captured().getFilterDsl()).isEqualTo("genre = \"x\" OR active = true");
    }

    @Test
    void containingBecomesFullTextWithSearchOnScope() {
        repo.findByTitleContaining("三体");
        MeiliQuery q = captured();
        assertThat(q.getQ()).isEqualTo("三体");
        assertThat(q.getAttributesToSearchOn()).containsExactly("book_title");
        assertThat(q.getFilterDsl()).isNull();
    }

    @Test
    void likeAliasMapsToSearchOnOverview() {
        repo.findByOverviewLike("x");
        assertThat(captured().getAttributesToSearchOn()).containsExactly("overview");
    }

    @Test
    void nestedChainProjectsDotPath() {
        repo.findByAuthorCity("北京");
        assertThat(captured().getFilterDsl()).isEqualTo("author.city = \"北京\"");
    }

    @Test
    void topBecomesLimitWhenNoPageable() {
        repo.findTop3ByGenre("g");
        assertThat(captured().getLimit()).isEqualTo(3);
    }

    @Test
    void methodOrderByRendersSort() {
        repo.findByGenreOrderByPriceDesc("g");
        assertThat(captured().getSort()).containsExactly("price:desc");
    }

    @Test
    void pageableConvertsToPageModeAndAppendsSort() {
        repo.findByGenre("g", PageRequest.of(1, 20, Sort.by("publishedAt")));
        MeiliQuery q = captured();
        assertThat(q.getPage()).isEqualTo(2);
        assertThat(q.getHitsPerPage()).isEqualTo(20);
        assertThat(q.getSort()).containsExactly("publishedAt:asc");
        assertThat(q.getFilterDsl()).isEqualTo("genre = \"g\"");
    }

    @Test
    void optionalReturnsFirstHit() {
        org.mockito.Mockito.doReturn(MeiliSearchResult.from("{\"hits\":[{\"id\":1},{\"id\":2}]}", DqBook.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())))
                .when(ops).search(any(MeiliQuery.class), any());
        Optional<DqBook> one = repo.findFirstByGenre("g");
        assertThat(one).isPresent();
    }

    @Test
    void quotedInjectionEscapedAsLiteral() {
        repo.findByGenre("科幻\" OR price > 0 --");
        assertThat(captured().getFilterDsl())
                .isEqualTo("genre = \"科幻\\\" OR price > 0 --\"");
    }

    @Nested
    @DisplayName("Bootstrap role pre-check (3.3)")
    class RolePrecheck {

        private Object build(Class<?> iface) {
            return MeiliRepositoryProxy.create(iface, ops, new MeiliMappingContext());
        }

        @Test
        @DisplayName("Missing filterable: startup fails, message names the property and both fixes")
        void missingFilterable() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByOverview(String s);
            }
            assertThatThrownBy(() -> build(R.class))
                    .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliMappingException.class)
                    .hasMessageContaining("overview")
                    .hasMessageContaining("filterable")
                    .hasMessageContaining("@MeiliSetting");
        }

        @Test
        void missingSortableOnOrderBy() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByGenreOrderByOverviewAsc(String g);
            }
            assertThatThrownBy(() -> build(R.class))
                    .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliMappingException.class)
                    .hasMessageContaining("sortable");
        }

        @Test
        void missingSearchableOnContaining() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByGenreContaining(String g);
            }
            assertThatThrownBy(() -> build(R.class))
                    .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliMappingException.class)
                    .hasMessageContaining("searchable");
        }

        @Test
        @DisplayName("Primary-key conditions are exempt from the filterable pre-check")
        void idConditionExempt() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> readById(Long id);
            }
            assertThat(build(R.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Out-of-scope surface (3.5)")
    class Unsupported {

        private void rejects(Class<?> iface, String messageFragment) {
            assertThatThrownBy(() -> MeiliRepositoryProxy.create(iface, ops, new MeiliMappingContext()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(messageFragment);
        }

        @Test
        void keywordSurface() {
            interface A extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByTitleStartingWith(String s);
            }
            interface B extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByGenreIsNotNull(String s);
            }
            interface C extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByGenreIgnoreCase(String s);
            }
            rejects(A.class, "StartingWith");
            rejects(B.class, "IsNotNull");
            rejects(C.class, "IgnoreCase");
        }

        @Test
        void distinctAndCountVerbsRejected() {
            interface D extends MeiliRepository<DqBook, Long> {
                List<DqBook> findDistinctByGenre(String g);
            }
            interface E extends MeiliRepository<DqBook, Long> {
                long countByGenre(String g);
            }
            rejects(D.class, "Distinct");
            rejects(E.class, "find/read/get/retrieve");
        }

        @Test
        void abbreviationRejectedWithExplicitMessage() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByAdrCity(String s);
            }
            rejects(R.class, "abbreviations are not supported");
        }

        @Test
        void excludedAndAggregateAndUnknownPropertiesRejected() {
            interface R1 extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByAuthorName(String s); // name has no role → pre-check error (locatable)
            }
            interface R2 extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByAuthor(DqAuthor a);
            }
            interface R3 extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByNoSuch(String s);
            }
            // name has no role at all → EQ pre-check fails; role-class errors go through MeiliMappingException
            assertThatThrownBy(() -> MeiliRepositoryProxy.create(R1.class, ops, new MeiliMappingContext()))
                    .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliMappingException.class)
                    .hasMessageContaining("filterable");
            rejects(R2.class, "aggregate");
            rejects(R3.class, "NoSuch");
        }

        @Test
        void badReturnShapesRejected() {
            interface R1 extends MeiliRepository<DqBook, Long> {
                DqBook findByGenre(String g);
            }
            interface R2 extends MeiliRepository<DqBook, Long> {
                Page<DqBook> findByGenreNoPage(String g);
            }
            rejects(R1.class, "return type");
            rejects(R2.class, "Pageable");
        }

        @Test
        void arityMismatchRejected() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByGenreAndPrice(String g);
            }
            rejects(R.class, "value argument count");
        }
    }
}
