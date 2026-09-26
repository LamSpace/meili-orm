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
 * spikeA sentinel: empirical verification of JsonHandler injection compatibility
 * (conclusion documented in docs/spikes.md, "spikeA conclusion").
 *
 * <p>Behavior baseline locked by measurement (meilisearch-java 0.21.0 × server v1.49.0):
 * (1) all SDK typed-read paths (TaskInfo/Task/Settings/document Map/Results) go through the
 * pluggable JsonHandler; (2) switching to JacksonJsonHandler makes the request-side
 * {@code Settings.encode} leak the Java dual-view field {@code filterableAttributesConfig},
 * rejected by the server with 400; (3) task/index write paths complete normally when they do
 * not touch that leak. Hence assembled Clients always keep the default GsonJsonHandler and
 * the entity read path uses the raw string API ({@code getRawDocument}/{@code rawSearch}).</p>
 *
 * <p>The assertions here are **behavior locks**, not desired design: any red assertion means
 * the SDK or server version behavior has drifted; re-run the empirical verification and
 * update docs/spikes.md — never just change the assertion.</p>
 */
class SpikeAJsonHandlerIT extends AbstractMeiliIntegrationTest {

    /** Long primary key &gt;2^53 here (spikeB owns the precision verdict; this class records channel behavior only). */
    private static final String SAMPLE_DOC =
            "{\"id\":9007199254740993,\"title\":\"三体\",\"genre\":\"科幻\"}";

    /**
     * Writes a document and waits for the task's terminal state, locking the
     * addDocuments→TaskInfo.decode→Task.decode path.
     *
     * @param client the client under test
     * @param uid    dedicated index name
     * @return the succeeded terminal task
     * @throws Exception on SDK invocation failure
     */
    private static Task writeAndWait(Client client, String uid) throws Exception {
        TaskInfo t = client.index(uid).addDocuments("[" + SAMPLE_DOC + "]");
        client.waitForTask(t.getTaskUid());
        Task done = client.getTask(t.getTaskUid());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        return done;
    }

    /** Counting handler: decode records the target type, then delegates to GsonJsonHandler (observes JsonHandler dispatch). */
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
    @DisplayName("Control group: default GsonJsonHandler parses all models normally (baseline)")
    void gsonHandlerBaseline() throws Exception {
        Client client = client();
        writeAndWait(client, "spikeA_gson");

        Settings s = new Settings();
        s.setFilterableAttributes(new String[]{"genre"});
        TaskInfo settingsTask = client.index("spikeA_gson").updateSettings(s);
        client.waitForTask(settingsTask.getTaskUid());   // settings updates are async; await the terminal state before reading to avoid a race
        Settings read = client.index("spikeA_gson").getSettings();
        assertThat(read.getFilterableAttributes()).containsExactly("genre");

        Map<?, ?> doc = client.index("spikeA_gson").getDocument("9007199254740993", Map.class);
        assertThat(doc.get("title")).isEqualTo("三体");

        Results<?> keys = client.getKeys();
        assertThat(keys.getResults()).isNotEmpty();
    }

    @Test
    @DisplayName("Experiment lock: under JacksonJsonHandler, Settings.encode leaks the dual-view field and the server rejects with 400, while task decode still passes")
    void jacksonHandlerLeaksDualViewField() throws Exception {
        Config config = new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY, new JacksonJsonHandler());
        Client client = new Client(config);

        // Step 1: addDocuments/waitForTask/getTask encode+decode never touch the Settings leak — passes in practice, locked
        Task done = writeAndWait(client, "spikeA_jackson");
        assertThat(done.getType()).isEqualTo("documentAdditionOrUpdate");

        // Step 2: updateSettings request-side Settings.encode leaks filterableAttributesConfig — lock the failure shape
        Settings s = new Settings();
        s.setFilterableAttributes(new String[]{"genre"});
        assertThatThrownBy(() -> client.index("spikeA_jackson").updateSettings(s))
                .isInstanceOf(MeilisearchApiException.class)
                .hasMessageContaining("Unknown field")
                .hasMessageContaining("filterableAttributesConfig");

        // Step 3: getSettings (pure read, decode Settings) does not throw in practice — locks the readability boundary
        assertThatCode(() -> client.index("spikeA_jackson").getSettings())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Probe lock: all five typed reads pass through JsonHandler (a custom handler couples to the whole read chain)")
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
