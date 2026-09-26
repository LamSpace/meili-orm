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

/** Contract tests for {@link SdkQueryTranslator}: IR→SDK mapping and rejection of illegal combinations. */
class SdkQueryTranslatorTest {

    @Test
    @DisplayName("Every known field lands equivalently on the SDK request object")
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
    @DisplayName("vector and hybrid constructs are passed through equivalently")
    void mapsVectorAndHybrid() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(MeiliQuery.query(null)
                .vector(List.of(1.5, 2.5))
                .hybrid("default-embedder", 0.7));
        assertThat(req.getQ()).isNull(); // empty query = pure filtered browse
        assertThat(req.getVector()).containsExactly(1.5, 2.5);
        assertThat(req.getHybrid().getEmbedder()).isEqualTo("default-embedder");
        assertThat(req.getHybrid().getSemanticRatio()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("filter groups: AND across groups, OR within a group (SDK filterArray shape)")
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
    @DisplayName("filterAdd accumulates with AND into a single DSL")
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
    @DisplayName("raw key conflicting with an explicit IR field → rejected (silent overrides are not allowed)")
    void rawConflictWithExplicitFieldRejected() {
        assertThatThrownBy(() -> SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").sort("price:asc").raw("sort", List.of("views:desc"))))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining("sort");
    }

    @Test
    @DisplayName("raw escape hatch: known key with the right type takes effect")
    void rawKnownKeyApplies() {
        SearchRequest req = SdkQueryTranslator.toSearchRequest(
                MeiliQuery.query("x").raw("rankingScoreThreshold", 0.8));
        assertThat(req.getRankingScoreThreshold()).isEqualTo(0.8);
    }
}
