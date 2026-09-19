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
 * spikeB 哨兵：raw 字符串 → Jackson 通道的精度与编码无损实证
 * （结论与 M1 引用锚点见 docs/spikes.md「spikeB 结论」）。
 *
 * <p>锁定的行为基线（meilisearch-java 0.21.0 × 服务端 v1.49.0）：
 * ① SDK typed 便捷 API（{@code getDocument(id, Class)}）经 Gson 的 Map 通道，
 * 超过 2^53 的整数主键被解为 {@code Double}——精度受损是**事实**而非猜想；
 * ② {@code getRawDocument(String)} 返回原始 JSON 字符串，Jackson 直读实体后
 * Long/中文/嵌套对象逐位无损；③ {@code rawSearch(SearchRequest)} 同理。
 * 因此本工程的实体读路径唯一契约 = raw 字符串 + 自有序列化器。</p>
 *
 * <p>任一断言变红 = SDK 或服务端读路径行为漂移，须重新实证并更新 docs/spikes.md，
 * 不得只改断言。注意本类刻意用裸 Jackson ObjectMapper 而非序列化器抽象——
 * 序列化器封装归核心模块本体。</p>
 */
class SpikeBRawJacksonPrecisionIT extends AbstractMeiliIntegrationTest {

    /** 嵌套作者（点路径投影的原始形态）。 */
    record Author(String name, String city) {}

    /** 被测实体：Long 主键 + long 字段 + 中文 + 嵌套对象 + 数组。 */
    record Book(Long id, String title, long views, Author author, List<String> tags) {}

    /** 大于 2^53：经 Double 必然失真。 */
    private static final long BIG = 9007199254740993L;

    private static final String DOC = "{\"id\":9007199254740993,\"title\":\"三体\",\"views\":9007199254740993,"
            + "\"author\":{\"name\":\"刘慈欣\",\"city\":\"北京\"},\"tags\":[\"科幻\",\"雨果奖\"]}";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("raw→Jackson 通道 Long/中文/嵌套无损；Gson Map 通道精度受损行为同步锁定")
    void rawChannelKeepsLongPrecision() throws Exception {
        Index index = client().index("spikeB");
        TaskInfo add = index.addDocuments("[" + DOC + "]");
        client().waitForTask(add.getTaskUid());

        // ① 对照：typed Map 通道把大整数解为 Double——"坏通道"事实锁定（绕开它的存在理由）
        Map<?, ?> viaGson = index.getDocument("9007199254740993", Map.class);
        assertThat(viaGson.get("id")).isInstanceOf(Double.class);
        assertThat(((Number) viaGson.get("id")).longValue()).isNotEqualTo(BIG);

        // ② 主路径：getRawDocument(String) 原始串 → Jackson 直读实体，逐位无损
        String rawDoc = index.getRawDocument("9007199254740993");
        Book b = MAPPER.readValue(rawDoc, Book.class);
        assertThat(b.id()).isEqualTo(BIG);
        assertThat(b.views()).isEqualTo(BIG);
        assertThat(b.title()).isEqualTo("三体");
        assertThat(b.author().name()).isEqualTo("刘慈欣");
        assertThat(b.author().city()).isEqualTo("北京");
        assertThat(b.tags()).containsExactly("科幻", "雨果奖");

        // ③ 搜索路径：rawSearch(SearchRequest) 信封 → hits 节点 treeToValue，同款无损
        String rawSearch = index.rawSearch(new SearchRequest("三体"));
        JsonNode hit = MAPPER.readTree(rawSearch).path("hits").get(0);
        Book h = MAPPER.treeToValue(hit, Book.class);
        assertThat(h.id()).isEqualTo(BIG);
        assertThat(h.views()).isEqualTo(BIG);
        assertThat(h.author().city()).isEqualTo("北京");
    }
}
