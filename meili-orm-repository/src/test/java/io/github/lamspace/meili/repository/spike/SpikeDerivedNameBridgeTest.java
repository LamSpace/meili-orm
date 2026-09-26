package io.github.lamspace.meili.repository.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lamspace.meili.repository.spike.SpikeNameParser.Clause;
import io.github.lamspace.meili.repository.spike.SpikeNameParser.Parsed;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * spike 黄金集（路线 b：自研语法 + core 语义桥）：20+ 典型派生方法名经
 * {@link SpikeNameParser} 解析、{@link SpikePropertyResolver} 落投影名。
 * 同一份源文件（含 parser/resolver/样本实体）复制到 Boot 4 矩阵模块运行，
 * 证明所用 commons 面（{@code org.springframework.data.domain.*}）在两代
 * 二进制下链接与行为一致。
 */
class SpikeDerivedNameBridgeTest {

    /** 解析 + 桥接全部条件子句，返回 dot 路径列表。 */
    private static List<String> resolved(Class<?> type, String method) {
        return SpikeNameParser.parse(type, method).clauses().stream()
                .map(c -> SpikePropertyResolver.resolve(type, c.chain().toArray(new String[0])))
                .toList();
    }

    private static Clause first(Class<?> type, String method) {
        return SpikeNameParser.parse(type, method).clauses().get(0);
    }

    @Nested
    @DisplayName("关键字解析 + 属性段桥接")
    class Keywords {

        @Test
        void implicitEqualsBridgesRenamedProperty() {
            assertThat(first(SpikeBook.class, "findByTitle").keyword()).isEqualTo("EQ");
            assertThat(resolved(SpikeBook.class, "findByTitle")).containsExactly("book_title");
        }

        @Test
        void idResolves() {
            assertThat(resolved(SpikeBook.class, "findById")).containsExactly("id");
        }

        @Test
        void andSplitsTwoClausesAndBothResolve() {
            Parsed p = SpikeNameParser.parse(SpikeBook.class, "findByGenreAndPriceGreaterThan");
            assertThat(p.clauses()).hasSize(2);
            assertThat(p.clauses().get(0).keyword()).isEqualTo("EQ");
            assertThat(p.clauses().get(1).keyword()).isEqualTo("GT");
            assertThat(resolved(SpikeBook.class, "findByGenreAndPriceGreaterThan"))
                    .containsExactly("genre", "price");
        }

        @Test
        void orMarksSecondClause() {
            Parsed p = SpikeNameParser.parse(SpikeBook.class, "findByGenreOrOverview");
            assertThat(p.clauses()).hasSize(2);
            assertThat(p.clauses().get(1).or()).isTrue();
            assertThat(resolved(SpikeBook.class, "findByGenreOrOverview"))
                    .containsExactly("genre", "overview");
        }

        @Test
        void betweenResolves() {
            Clause c = first(SpikeBook.class, "findByPriceBetween");
            assertThat(c.keyword()).isEqualTo("BETWEEN");
            assertThat(c.chain()).containsExactly("price");
        }

        @Test
        void inResolvesOpaqueCollectionLeaf() {
            assertThat(first(SpikeBook.class, "findByTagsIn").keyword()).isEqualTo("IN");
            assertThat(resolved(SpikeBook.class, "findByTagsIn")).containsExactly("tags");
        }

        @Test
        void trueFalseAndNegationCombinations() {
            Parsed p = SpikeNameParser.parse(SpikeBook.class, "findByActiveTrueAndNotActiveFalse");
            assertThat(p.clauses().get(0).keyword()).isEqualTo("TRUE");
            assertThat(p.clauses().get(1).negate()).isTrue();
            assertThat(p.clauses().get(1).keyword()).isEqualTo("FALSE");
        }

        @Test
        void trailingNotResolvesNegating() {
            assertThat(first(SpikeBook.class, "findByGenreNot").keyword()).isEqualTo("NE");
            assertThat(resolved(SpikeBook.class, "findByGenreNot")).containsExactly("genre");
        }

        @Test
        void nestedChainResolvesToDotPath() {
            assertThat(resolved(SpikeBook.class, "findByAuthorCity")).containsExactly("author.city");
        }

        @Test
        void nestedCombinedWithTop() {
            Parsed p = SpikeNameParser.parse(SpikeBook.class, "findTop5ByAuthorCityAndGenre");
            assertThat(p.maxResults()).isEqualTo(5);
            assertThat(resolved(SpikeBook.class, "findTop5ByAuthorCityAndGenre"))
                    .containsExactly("author.city", "genre");
        }

        @Test
        void containingResolvesRenamed() {
            Clause c = first(SpikeBook.class, "findByTitleContaining");
            assertThat(c.keyword()).isEqualTo("CONTAINING");
            assertThat(SpikePropertyResolver.resolve(SpikeBook.class, c.chain().toArray(new String[0])))
                    .isEqualTo("book_title");
        }

        @Test
        void likeResolves() {
            assertThat(first(SpikeBook.class, "findByOverviewLike").keyword()).isEqualTo("LIKE");
        }

        @Test
        void nestedContainingResolves() {
            assertThat(resolved(SpikeBook.class, "findByAuthorCityContaining"))
                    .containsExactly("author.city");
        }

        @Test
        void beforeAfterMapToComparisons() {
            assertThat(first(SpikeBook.class, "findByPublishedAtAfter").keyword()).isEqualTo("GT");
            assertThat(first(SpikeBook.class, "findByPublishedAtBefore").keyword()).isEqualTo("LT");
            assertThat(resolved(SpikeBook.class, "findByPublishedAtAfter")).containsExactly("publishedAt");
        }

        @Test
        void lessGreaterEqualVariants() {
            assertThat(first(SpikeBook.class, "findByPriceLessThanEqual").keyword()).isEqualTo("LTE");
            assertThat(first(SpikeBook.class, "findByPriceGreaterThanEqual").keyword()).isEqualTo("GTE");
            assertThat(first(SpikeBook.class, "findByPriceLessThan").keyword()).isEqualTo("LT");
        }

        @Test
        void orderByChainsAndDirections() {
            Parsed p = SpikeNameParser.parse(SpikeBook.class,
                    "findTop3ByGenreOrderByPriceDescAndAuthorCityAsc");
            assertThat(p.maxResults()).isEqualTo(3);
            assertThat(p.orders()).hasSize(2);
            assertThat(p.orders().get(0).asc()).isFalse();
            assertThat(p.orders().get(1).chain()).containsExactly("author", "city");
        }

        @Test
        void recordEntityComponentsResolve() {
            assertThat(resolved(SpikeRecordBook.class, "findByTitle")).containsExactly("record_title");
        }

        @Test
        void noRolePropertyStillResolves() {
            assertThat(resolved(SpikeBook.class, "findByAuthorName")).containsExactly("author.name");
        }

        @Test
        void complexMixedName() {
            Parsed p = SpikeNameParser.parse(SpikeBook.class,
                    "findByGenreAndNotPriceGreaterThanOrderByPublishedAtDesc");
            assertThat(p.clauses()).hasSize(2);
            assertThat(p.clauses().get(1).negate()).isTrue();
            assertThat(p.orders().get(0).asc()).isFalse();
        }
    }

    @Nested
    @DisplayName("负向与限制面")
    class Negative {

        @Test
        void unknownPropertyRejects() {
            assertThatThrownBy(() -> SpikeNameParser.parse(SpikeBook.class, "findByNoSuch"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无法解析属性段");
        }

        @Test
        void jsonIgnoredPropertyResolvesThenRejectedByBridge() {
            assertThatThrownBy(() -> resolved(SpikeBook.class, "findBySecret"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("secret");
        }

        @Test
        void aggregateAsLeafRejected() {
            assertThatThrownBy(() -> resolved(SpikeBook.class, "findByAuthor"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("聚合");
        }

        @Test
        @DisplayName("缩写不支持（自研语法无 commons 属性缩写还原）")
        void abbreviationUnsupported() {
            assertThatThrownBy(() -> SpikeNameParser.parse(SpikeBook.class, "findByAdrCity"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("缩写");
        }

        @Test
        void unsupportedKeywordsRejected() {
            assertThatThrownBy(() -> SpikeNameParser.parse(SpikeBook.class, "findByTitleStartingWith"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> SpikeNameParser.parse(SpikeBook.class, "findByGenreIgnoreCase"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> SpikeNameParser.parse(SpikeBook.class, "findByGenreIsNotNull"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void distinctFlagObservable() {
            assertThat(SpikeNameParser.parse(SpikeBook.class, "findDistinctByGenre").distinct()).isTrue();
        }
    }

    @Nested
    @DisplayName("commons 稳定面链接与行为（双代共用清单）")
    class CommonsSurface {

        @Test
        void sortPageablePageSurfaceIdenticalAcrossGenerations() {
            Sort sort = Sort.by("price").descending();
            assertThat(sort.getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
            Pageable pr = PageRequest.of(2, 20);
            assertThat(pr.getOffset()).isEqualTo(40L);
            assertThat(pr.getPageNumber()).isEqualTo(2);
            Page<String> page = new PageImpl<>(List.of("x"), PageRequest.of(0, 1), 5);
            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getContent()).containsExactly("x");
        }
    }
}
