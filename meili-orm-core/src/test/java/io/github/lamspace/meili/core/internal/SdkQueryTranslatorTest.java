package io.github.lamspace.meili.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meilisearch.sdk.SearchRequest;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MatchingStrategy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link SdkQueryTranslator} 的 IR→SDK 映射与非法组合拒绝契约测试。 */
class SdkQueryTranslatorTest {

    @Test
    @DisplayName("已知字段逐项等价落到 SDK 请求对象")
    void mapsAllKnownFields() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(MeiliQuery.query("三体")
                .filter("genre = \"科幻\"")
                .sort("price:asc")
                .limit(5).offset(10)
                .attributes("book_title", "price")
                .attributesToSearchOn("overview")
                .facets("genre")
                .showRankingScore(true)
                .showMatchesPosition(true)
                .distinct("authorId")
                .matchingStrategy(MatchingStrategy.ALL));

        assertThat(req.getQ()).isEqualTo("三体");
        assertThat(req.getFilter()).containsExactly("genre = \"科幻\"");
        assertThat(req.getSort()).containsExactly("price:asc");
        assertThat(req.getLimit()).isEqualTo(5);
        assertThat(req.getOffset()).isEqualTo(10);
        assertThat(req.getAttributesToRetrieve()).contains("book_title", "price");
        assertThat(req.getAttributesToSearchOn()).containsExactly("overview");
        assertThat(req.getFacets()).containsExactly("genre");
        assertThat(req.getShowRankingScore()).isTrue();
        assertThat(req.getShowMatchesPosition()).isTrue();
        assertThat(req.getDistinct()).isEqualTo("authorId");
        assertThat(req.getMatchingStrategy()).isEqualTo(com.meilisearch.sdk.model.MatchingStrategy.ALL);
    }

    @Test
    @DisplayName("vector 与 hybrid 构造等价传递")
    void mapsVectorAndHybrid() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(MeiliQuery.query(null)
                .vector(List.of(1.5, 2.5))
                .hybrid("default-embedder", 0.7));
        assertThat(req.getQ()).isNull(); // 空查询 = 纯过滤浏览
        assertThat(req.getVector()).containsExactly(1.5, 2.5);
        assertThat(req.getHybrid().getEmbedder()).isEqualTo("default-embedder");
        assertThat(req.getHybrid().getSemanticRatio()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("filter 分组：组间 AND、组内 OR（SDK filterArray 形态）")
    void mapsFilterGroups() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(MeiliQuery.query("x")
                .filterGroup(List.of("genre = \"科幻\"", "genre = \"历史\""))
                .filterGroup(List.of("price > 30")));
        assertThat(req.getFilter()).isNull();
        String[][] groups = req.getFilterArray();
        assertThat(groups.length).isEqualTo(2);
        assertThat(groups[0]).containsExactly("genre = \"科幻\"", "genre = \"历史\"");
        assertThat(groups[1]).containsExactly("price > 30");
    }

    @Test
    @DisplayName("filterAdd 以 AND 累积到单一 DSL")
    void filterAddAccumulatesWithAnd() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(MeiliQuery.query("x")
                .filterAdd("genre = \"科幻\"").filterAdd("price > 30"));
        assertThat(req.getFilter()).containsExactly("genre = \"科幻\" AND price > 30");
    }

    @Test
    void paginationModesExclusive() {
        assertThatThrownBy(() -> SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").page(1).limit(5)))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("limit/offset")
                .hasMessageContaining("page/hitsPerPage");
    }

    @Test
    void filterDslVsGroupsExclusive() {
        assertThatThrownBy(() -> SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").filter("a = 1").filterGroup(List.of("b = 2"))))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("filter");
    }

    @Test
    void unknownRawKeyRejected() {
        assertThatThrownBy(() -> SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").raw("nonsense", 1)))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("nonsense");
    }

    @Test
    @DisplayName("raw 与显式 IR 同键冲突 → 拒绝（不允许静默覆盖）")
    void rawConflictWithExplicitFieldRejected() {
        assertThatThrownBy(() -> SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").sort("price:asc").raw("sort", List.of("views:desc"))))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("sort");
    }

    @Test
    @DisplayName("raw 逃生舱：合法键 + 正确类型生效")
    void rawKnownKeyApplies() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").raw("rankingScoreThreshold", 0.8));
        assertThat(req.getRankingScoreThreshold()).isEqualTo(0.8);
    }
}
