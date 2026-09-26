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
package io.github.lamspace.meili.core.spike;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.TaskInfo;
import io.github.lamspace.meili.core.AbstractMeiliIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spikeB sentinel: empirical proof that the raw-string → Jackson channel preserves
 * precision and encoding losslessly (conclusion and M1 reference anchor in
 * docs/spikes.md, "spikeB conclusion").
 *
 * <p>Locked behavior baseline (meilisearch-java 0.21.0 × server v1.49.0):
 * (1) the SDK typed convenience API ({@code getDocument(id, Class)}) goes through Gson's
 * Map channel and decodes integer primary keys &gt;2^53 as {@code Double} — precision loss
 * is a **fact**, not a hypothesis; (2) {@code getRawDocument(String)} returns the raw JSON
 * string and Jackson reading the entity directly keeps Long/Chinese/nested objects
 * bit-exact; (3) {@code rawSearch(SearchRequest)} behaves the same. Therefore the entity
 * read path's only contract in this project = raw string + own serializer.</p>
 *
 * <p>Any red assertion = drift in SDK or server read-path behavior; re-run the empirical
 * verification and update docs/spikes.md — never just change the assertion. Note this
 * class deliberately uses a bare Jackson ObjectMapper instead of the serializer
 * abstraction — serializer packaging belongs to the core module itself.</p>
 */
class SpikeBRawJacksonPrecisionIT extends AbstractMeiliIntegrationTest {

    /** Nested author (the raw shape a dotted-path projection addresses). */
    record Author(String name, String city) {}

    /** Entity under test: Long primary key + long field + Chinese text + nested object + array. */
    record Book(Long id, String title, long views, Author author, List<String> tags) {}

    /** Greater than 2^53: going through Double necessarily distorts it. */
    private static final long BIG = 9007199254740993L;

    private static final String DOC = "{\"id\":9007199254740993,\"title\":\"三体\",\"views\":9007199254740993,"
            + "\"author\":{\"name\":\"刘慈欣\",\"city\":\"北京\"},\"tags\":[\"科幻\",\"雨果奖\"]}";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("raw→Jackson channel keeps Long/Chinese/nested values lossless; Gson Map channel precision-loss behavior locked in parallel")
    void rawChannelKeepsLongPrecision() throws Exception {
        Index index = client().index("spikeB");
        TaskInfo add = index.addDocuments("[" + DOC + "]");
        client().waitForTask(add.getTaskUid());

        // (1) control: the typed Map channel decodes the big integer as Double — locks the "bad channel" fact (the reason to bypass it)
        Map<?, ?> viaGson = index.getDocument("9007199254740993", Map.class);
        assertThat(viaGson.get("id")).isInstanceOf(Double.class);
        assertThat(((Number) viaGson.get("id")).longValue()).isNotEqualTo(BIG);

        // (2) main path: getRawDocument(String) raw text → Jackson straight into the entity, bit-exact
        String rawDoc = index.getRawDocument("9007199254740993");
        Book b = MAPPER.readValue(rawDoc, Book.class);
        assertThat(b.id()).isEqualTo(BIG);
        assertThat(b.views()).isEqualTo(BIG);
        assertThat(b.title()).isEqualTo("三体");
        assertThat(b.author().name()).isEqualTo("刘慈欣");
        assertThat(b.author().city()).isEqualTo("北京");
        assertThat(b.tags()).containsExactly("科幻", "雨果奖");

        // (3) search path: rawSearch(SearchRequest) envelope → treeToValue on the hits node, same losslessness
        String rawSearch = index.rawSearch(new SearchRequest("三体"));
        JsonNode hit = MAPPER.readTree(rawSearch).path("hits").get(0);
        Book h = MAPPER.treeToValue(hit, Book.class);
        assertThat(h.id()).isEqualTo(BIG);
        assertThat(h.views()).isEqualTo(BIG);
        assertThat(h.author().city()).isEqualTo("北京");
    }
}
