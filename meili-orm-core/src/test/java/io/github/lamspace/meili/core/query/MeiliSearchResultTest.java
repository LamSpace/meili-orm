package io.github.lamspace.meili.core.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MeiliSearchResult} 信封解析与 hits 无损反序列化契约测试。 */
class MeiliSearchResultTest {

    record Book(Long id, String title) {}

    private static final Jackson2DocumentSerializer SER =
            new Jackson2DocumentSerializer(new ObjectMapper());

    private static final String RAW = "{\"hits\":[{\"id\":9007199254740993,\"title\":\"三体\"}],"
            + "\"offset\":0,\"limit\":20,\"estimatedTotalHits\":1,\"processingTimeMs\":3,\"query\":\"三体\","
            + "\"facetDistribution\":{\"genre\":{\"科幻\":1}},\"facetStats\":{\"price\":{\"min\":59.0,\"max\":59.0}}}";

    @Test
    @DisplayName("hits 经序列化器无损；信封字段完整")
    void parsesEnvelopeAndTypedHitsLossless() {
        var r = MeiliSearchResult.from(RAW, Book.class, SER);
        assertThat(r.getHits()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo(9007199254740993L); // Long 逐位无损
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
    @DisplayName("缺失的信封键按 null 呈现（非分页响应无 totalPages 等）")
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
    @DisplayName("分页形态响应：page 系字段与 total 系字段并存")
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
