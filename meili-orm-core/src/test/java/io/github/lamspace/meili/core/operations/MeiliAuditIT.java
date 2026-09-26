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
package io.github.lamspace.meili.core.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import io.github.lamspace.meili.core.AbstractMeiliIntegrationTest;
import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.internal.SdkMeiliRawGateway;
import io.github.lamspace.meili.core.mapping.CreatedDate;
import io.github.lamspace.meili.core.mapping.LastModifiedDate;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3 real-server IT for the audit feature (wait-task=true, v1.49.0): the dual timestamps
 * filled by save land in the server-side document, findById reads back values identical to
 * what was saved (reusing the Long precision chain), and after a further save created stays
 * stable while modified lands in the new time window.
 */
class MeiliAuditIT extends AbstractMeiliIntegrationTest {

    private static final ObjectMapper M = new ObjectMapper();

    @MeiliDocument(indexName = "m6audit_books")
    record AuditBook(@MeiliId Long id, String title,
                     @CreatedDate Long createdAt, @LastModifiedDate long updatedAt) {}

    @Test
    @DisplayName("Real-server path: filled values enter the server document → read-back matches → re-save keeps created stable and moves modified into the new window")
    void auditEndToEnd() throws Exception {
        Client client = client();
        Config config = new Config(MeiliContainer.url(), MeiliContainer.MASTER_KEY);
        MeiliRawGateway gateway = new SdkMeiliRawGateway(client, config);
        var ops = new DefaultMeiliSearchOperations(gateway, new MeiliMappingContext(),
                new Jackson2DocumentSerializer(M), MeiliEntityCallbacks.none(),
                true, Duration.ofSeconds(20));
        ops.awaitTask(ops.createIndex(AuditBook.class));
        try {
            Instant before1 = Instant.now();
            AuditBook saved = ops.save(new AuditBook(9007199254740993L, "三体", null, 0L));
            Instant after1 = Instant.now();
            assertThat(saved.createdAt()).isNotNull()
                    .isBetween(before1.toEpochMilli(), after1.toEpochMilli());
            assertThat(saved.updatedAt()).isBetween(before1.toEpochMilli(), after1.toEpochMilli());

            // server document: both timestamps persisted and non-null (direct check via the raw GET channel)
            JsonNode doc = M.readTree(gateway.fetchRawDocument("m6audit_books",
                    "9007199254740993").orElseThrow());
            assertThat(doc.get("createdAt").isNull()).isFalse();
            assertThat(doc.get("updatedAt").isNull()).isFalse();

            // findById read-back is bit-identical to the saved values (epoch-millis Long precision chain reused)
            AuditBook back = ops.findById(9007199254740993L, AuditBook.class).orElseThrow();
            assertThat(back.createdAt()).isEqualTo(saved.createdAt());
            assertThat(back.updatedAt()).isEqualTo(saved.updatedAt());

            // re-save: created kept at the same value, modified lands in the second call's time window
            Instant before2 = Instant.now();
            AuditBook resaved = ops.save(back);
            Instant after2 = Instant.now();
            AuditBook back2 = ops.findById(9007199254740993L, AuditBook.class).orElseThrow();
            assertThat(back2.createdAt()).isEqualTo(saved.createdAt());
            assertThat(resaved.createdAt()).isEqualTo(saved.createdAt());
            assertThat(back2.updatedAt()).isBetween(before2.toEpochMilli(), after2.toEpochMilli());
        } finally {
            ops.deleteIndex(AuditBook.class);
        }
    }
}
