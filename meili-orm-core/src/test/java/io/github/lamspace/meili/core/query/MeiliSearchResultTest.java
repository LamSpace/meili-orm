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
package io.github.lamspace.meili.core.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link MeiliSearchResult} envelope parsing and lossless hits deserialization. */
class MeiliSearchResultTest {

    record Book(Long id, String title) {}

    private static final Jackson2DocumentSerializer SER =
            new Jackson2DocumentSerializer(new ObjectMapper());

    private static final String RAW = "{\"hits\":[{\"id\":9007199254740993,\"title\":\"三体\"}],"
            + "\"offset\":0,\"limit\":20,\"estimatedTotalHits\":1,\"processingTimeMs\":3,\"query\":\"三体\","
            + "\"facetDistribution\":{\"genre\":{\"科幻\":1}},\"facetStats\":{\"price\":{\"min\":59.0,\"max\":59.0}}}";

    @Test
    @DisplayName("hits stay lossless through the serializer; envelope fields complete")
    void parsesEnvelopeAndTypedHitsLossless() {
        var r = MeiliSearchResult.from(RAW, Book.class, SER);
        assertThat(r.getHits()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo(9007199254740993L); // Long bit-exact
            assertThat(b.title()).isEqualTo("三体");
        });
        assertThat(r.getEstimatedTotalHits()).isEqualTo(1L);
        assertThat(r.getOffset()).isEqualTo(0);
        assertThat(r.getLimit()).isEqualTo(20);
        assertThat(r.getProcessingTimeMs()).isEqualTo(3L);
        assertThat(r.getQuery()).isEqualTo("三体");
        assertThat(r.getFacetDistribution()).containsEntry("genre", Map.of("科幻", 1));
        assertThat(r.getFacetStats()).containsKey("price");
        assertThat(r.getRawJson()).isEqualTo(RAW);
    }

    @Test
    @DisplayName("Absent envelope keys surface as null (non-paginated responses have no totalPages etc.)")
    void absentEnvelopeFieldsAreNull() {
        var r = MeiliSearchResult.from("{\"hits\":[]}", Book.class, SER);
        assertThat(r.getHits()).isEmpty();
        assertThat(r.getTotalPages()).isNull();
        assertThat(r.getPage()).isNull();
        assertThat(r.getHitsPerPage()).isNull();
        assertThat(r.getTotalHits()).isNull();
        assertThat(r.getEstimatedTotalHits()).isNull();
        assertThat(r.getFacetDistribution()).isEmpty();
    }

    @Test
    @DisplayName("Paginated response shape: page-family and total-family fields coexist")
    void paginatedEnvelopeFields() {
        var r = MeiliSearchResult.from(
                "{\"hits\":[],\"page\":2,\"hitsPerPage\":10,\"totalPages\":5,\"totalHits\":42}",
                Book.class, SER);
        assertThat(r.getPage()).isEqualTo(2);
        assertThat(r.getHitsPerPage()).isEqualTo(10);
        assertThat(r.getTotalPages()).isEqualTo(5);
        assertThat(r.getTotalHits()).isEqualTo(42L);
    }

    @Test
    void malformedRawFailsAsOrmException() {
        assertThatThrownBy(() -> MeiliSearchResult.from("{oops", Book.class, SER))
                .isInstanceOf(MeiliOrmException.class);
    }
}
