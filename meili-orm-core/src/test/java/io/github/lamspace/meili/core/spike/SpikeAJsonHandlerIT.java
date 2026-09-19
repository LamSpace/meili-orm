package io.github.lamspace.meili.core.spike;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.json.GsonJsonHandler;
import com.meilisearch.sdk.json.JacksonJsonHandler;
import com.meilisearch.sdk.json.JsonHandler;
import com.meilisearch.sdk.model.Results;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import io.github.lamspace.meili.core.AbstractMeiliIntegrationTest;
import io.github.lamspace.meili.core.MeiliContainer;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * spikeA 哨兵：JsonHandler 注入兼容性实证（结论见 docs/spikes.md「spikeA 结论」）。
 *
 * <p>实测锁定的行为基线（meilisearch-java 0.21.0 × 服务端 v1.49.0）：
 * ① SDK 全部 typed 读环节（TaskInfo/Task/Settings/文档 Map/Results）经由可插拔
 * JsonHandler；② 换 JacksonJsonHandler 后请求侧 {@code Settings.encode} 泄漏 Java 双视
 * 图字段 {@code filterableAttributesConfig}，服务端 400 拒绝；③ 任务/索引写环节不经过
 * 该泄漏时可正常完成。因此装配 Client 一律保持默认 GsonJsonHandler，实体读路径走
 * raw 字符串 API（{@code getRawDocument}/{@code rawSearch}）。</p>
 *
 * <p>本类断言为**行为锁定**而非期望设计：任一断言变红意味着 SDK 或服务端版本行为
 * 漂移，须重新走实证并更新 docs/spikes.md，不得只改断言。</p>
 */
class SpikeAJsonHandlerIT extends AbstractMeiliIntegrationTest {

    /** 含 &gt;2^53 的 Long 主键（精度主判权在 spikeB，本类只记录通道行为）。 */
    private static final String SAMPLE_DOC =
            "{\"id\":9007199254740993,\"title\":\"三体\",\"genre\":\"科幻\"}";

    /**
     * 写文档并等待任务终态，锁定 addDocuments→TaskInfo.decode→Task.decode 环节。
     *
     * @param client 被测装配的客户端
     * @param uid    独立索引名
     * @return 已成功的任务终态
     * @throws Exception SDK 调用异常
     */
    private static Task writeAndWait(Client client, String uid) throws Exception {
        TaskInfo t = client.index(uid).addDocuments("[" + SAMPLE_DOC + "]");
        client.waitForTask(t.getTaskUid());
        Task done = client.getTask(t.getTaskUid());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        return done;
    }

    /** 计数委托 handler：decode 记录目标类型后委托 GsonJsonHandler（观察经过 JsonHandler 的环节）。 */
    static final class CountingGsonDelegatingHandler implements JsonHandler {
        private final GsonJsonHandler delegate = new GsonJsonHandler();
        final Set<String> decodeTargets = new LinkedHashSet<>();
        int encodeCalls;

        @Override
        public String encode(Object o) throws com.meilisearch.sdk.exceptions.MeilisearchException {
            encodeCalls++;
            return delegate.encode(o);
        }

        @Override
        public <T> T decode(Object o, Class<T> targetClass, Class<?>... parameterClasses)
                throws com.meilisearch.sdk.exceptions.MeilisearchException {
            decodeTargets.add(targetClass.getSimpleName()
                    + (parameterClasses.length > 0 ? "<" + parameterClasses[0].getSimpleName() + ">" : ""));
            return delegate.decode(o, targetClass, parameterClasses);
        }
    }

    @Test
    @DisplayName("对照组：默认 GsonJsonHandler 全模型解析正常（基线）")
    void gsonHandlerBaseline() throws Exception {
        Client client = client();
        writeAndWait(client, "spikeA_gson");

        Settings s = new Settings();
        s.setFilterableAttributes(new String[]{"genre"});
        TaskInfo settingsTask = client.index("spikeA_gson").updateSettings(s);
        client.waitForTask(settingsTask.getTaskUid());   // settings 更新为异步任务，等终态再读，避免竞态
        Settings read = client.index("spikeA_gson").getSettings();
        assertThat(read.getFilterableAttributes()).containsExactly("genre");

        Map<?, ?> doc = client.index("spikeA_gson").getDocument("9007199254740993", Map.class);
        assertThat(doc.get("title")).isEqualTo("三体");

        Results<?> keys = client.getKeys();
        assertThat(keys.getResults()).isNotEmpty();
    }

    @Test
    @DisplayName("实验组锁定：JacksonJsonHandler 下 Settings.encode 泄漏双视图字段致服务端 400，任务 decode 环节仍正常")
    void jacksonHandlerLeaksDualViewField() throws Exception {
        Config config = new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY, new JacksonJsonHandler());
        Client client = new Client(config);

        // 环节①：addDocuments/waitForTask/getTask 的 encode+decode 不经过 Settings 泄漏，实测通过——锁定
        Task done = writeAndWait(client, "spikeA_jackson");
        assertThat(done.getType()).isEqualTo("documentAdditionOrUpdate");

        // 环节②：updateSettings 请求侧 Settings.encode 泄漏 filterableAttributesConfig——锁定异常形态
        Settings s = new Settings();
        s.setFilterableAttributes(new String[]{"genre"});
        assertThatThrownBy(() -> client.index("spikeA_jackson").updateSettings(s))
                .isInstanceOf(MeilisearchApiException.class)
                .hasMessageContaining("Unknown field")
                .hasMessageContaining("filterableAttributesConfig");

        // 环节③：getSettings（纯读，decode Settings）实测不抛——锁定可读性边界
        assertThatCode(() -> client.index("spikeA_jackson").getSettings())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("探针组锁定：五类 typed 读全部经过 JsonHandler（自定义 handler 的耦合面为全读链）")
    void countingHandlerObservesDispatch() throws Exception {
        CountingGsonDelegatingHandler handler = new CountingGsonDelegatingHandler();
        Config config = new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY, handler);
        Client client = new Client(config);
        writeAndWait(client, "spikeA_counting");

        Settings s = new Settings();
        s.setFilterableAttributes(new String[]{"genre"});
        client.index("spikeA_counting").updateSettings(s);
        client.index("spikeA_counting").getSettings();
        client.index("spikeA_counting").getDocument("9007199254740993", Map.class);
        client.getKeys();

        assertThat(handler.decodeTargets)
                .contains("TaskInfo", "Task", "Settings", "Map", "Results<Key>");
        assertThat(handler.encodeCalls).isGreaterThanOrEqualTo(1);
    }
}
