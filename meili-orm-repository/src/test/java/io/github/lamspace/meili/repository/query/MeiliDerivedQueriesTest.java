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
 * L1 黄金集（覆盖任务 3.2–3.5）：关键字→MeiliQuery 逐条渲染、桥接、启动期角色预检、
 * 分页/排序换算、空 IN 短路、不支持面的可定位报错。
 */
class MeiliDerivedQueriesTest {

    @MeiliDocument(indexName = "dq_books")
    public static class DqBook {
        /** 主键。 */
        @MeiliId
        public Long id;
        /** 改名 + searchable。 */
        @MeiliField(name = "book_title", searchable = true)
        public String title;
        /** searchable，无 filterable。 */
        @MeiliField(searchable = true)
        public String overview;
        /** filterable。 */
        @MeiliField(filterable = true)
        public String genre;
        /** filterable+sortable。 */
        @MeiliField(filterable = true, sortable = true)
        public Double price;
        /** filterable 布尔。 */
        @MeiliField(filterable = true)
        public Boolean active;
        /** sortable 时间。 */
        @MeiliField(sortable = true)
        public java.time.OffsetDateTime publishedAt;
        /** filterable 集合不透明叶。 */
        @MeiliField(filterable = true)
        public List<String> tags;
        /** 嵌套聚合。 */
        public DqAuthor author;
    }

    /** 嵌套类型。 */
    public static class DqAuthor {
        /** filterable+sortable。 */
        @MeiliField(filterable = true, sortable = true)
        public String city;
        /** 无角色。 */
        public String name;
    }

    /** 正常面仓库。 */
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
        // 空 IN 短路：不产生第二次检索
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
    @DisplayName("启动期角色预检（3.3）")
    class RolePrecheck {

        private Object build(Class<?> iface) {
            return MeiliRepositoryProxy.create(iface, ops, new MeiliMappingContext());
        }

        @Test
        @DisplayName("缺 filterable：启动失败，消息含属性与两条修复指引")
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
        @DisplayName("主键条件豁免 filterable 预检")
        void idConditionExempt() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> readById(Long id);
            }
            assertThat(build(R.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("不支持面（3.5）")
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
            rejects(E.class, "仅支持 find/read/get/retrieve");
        }

        @Test
        void abbreviationRejectedWithExplicitMessage() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByAdrCity(String s);
            }
            rejects(R.class, "不支持缩写");
        }

        @Test
        void excludedAndAggregateAndUnknownPropertiesRejected() {
            interface R1 extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByAuthorName(String s); // name 无角色 → 预检报错（可定位）
            }
            interface R2 extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByAuthor(DqAuthor a);
            }
            interface R3 extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByNoSuch(String s);
            }
            // name 无任何角色 → EQ 预检失败，角色类错误走 MeiliMappingException
            assertThatThrownBy(() -> MeiliRepositoryProxy.create(R1.class, ops, new MeiliMappingContext()))
                    .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliMappingException.class)
                    .hasMessageContaining("filterable");
            rejects(R2.class, "聚合");
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
            rejects(R1.class, "返回类型");
            rejects(R2.class, "Pageable");
        }

        @Test
        void arityMismatchRejected() {
            interface R extends MeiliRepository<DqBook, Long> {
                List<DqBook> findByGenreAndPrice(String g);
            }
            rejects(R.class, "值参数数量");
        }
    }
}
