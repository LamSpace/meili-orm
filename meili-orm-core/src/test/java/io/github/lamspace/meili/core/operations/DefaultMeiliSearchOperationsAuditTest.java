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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.CreatedDate;
import io.github.lamspace.meili.core.mapping.LastModifiedDate;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Mock-gateway contract tests for write-path audit filling (@CreatedDate/@LastModifiedDate). */
class DefaultMeiliSearchOperationsAuditTest {

    static final ObjectMapper M = new ObjectMapper();
    static final Jackson2DocumentSerializer SER = new Jackson2DocumentSerializer(M);

    @MeiliDocument(indexName = "audit_pojo")
    static class PojoBook {
        @MeiliId public Long id;
        public String title;
        @CreatedDate public OffsetDateTime createdAt;
        @LastModifiedDate public OffsetDateTime updatedAt;
        public PojoBook() { }
        PojoBook(Long id, String title) { this.id = id; this.title = title; }
    }

    @MeiliDocument(indexName = "audit_rec")
    record RecBook(@MeiliId Long id, String title,
                   @CreatedDate Long createdAt, @LastModifiedDate long updatedAt) {}

    @MeiliDocument(indexName = "audit_plain")
    record PlainBook(@MeiliId Long id, String title) {}

    MeiliRawGateway gw;
    DefaultMeiliSearchOperations ops;

    @BeforeEach
    void up() {
        gw = mock(MeiliRawGateway.class);
        ops = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                MeiliEntityCallbacks.none(), false, Duration.ofSeconds(5));
        when(gw.updateDocuments(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("Fresh entity: created/modified both land in the call's time window, written doc carries both, POJO returns the same instance")
    void freshPojoBothFilledInWindow() {
        PojoBook b = new PojoBook(1L, "三体");
        Instant before = Instant.now();
        PojoBook saved = ops.save(b);
        Instant after = Instant.now();

        assertThat(saved).isSameAs(b);
        assertThat(saved.createdAt.toInstant()).isBetween(before, after);
        assertThat(saved.updatedAt.toInstant()).isBetween(before, after);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gw).updateDocuments(eq("audit_pojo"), body.capture());
        JsonNode doc = parse(body.getValue());
        assertThat(doc.get("createdAt").isNull()).isFalse();
        assertThat(doc.get("updatedAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("User-supplied non-null created is kept verbatim; the 0 sentinel of long gets filled, non-zero values stay")
    void userSuppliedCreatedKeptZeroSentinelFilled() {
        PojoBook preset = new PojoBook(2L, "活着");
        OffsetDateTime old = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        preset.createdAt = old;
        PojoBook savedPreset = ops.save(preset);
        assertThat(savedPreset.createdAt).isEqualTo(old);
        assertThat(savedPreset.updatedAt).isNotEqualTo(old);

        // Long component null → filled; long component 0 → sentinel treated as unset and filled
        RecBook blank = ops.save(new RecBook(3L, "a", null, 0L));
        assertThat(blank.createdAt()).isNotNull().isNotZero();
        assertThat(blank.updatedAt()).isNotZero();

        // user-supplied non-null/non-zero values stay
        RecBook kept = ops.save(new RecBook(4L, "b", 5L, 777L));
        assertThat(kept.createdAt()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Re-save after reload: created equals the first fill, modified lands in the new time window")
    void reloadResaveCreatedStableModifiedAdvances() {
        RecBook first = ops.save(new RecBook(5L, "沙丘", null, 0L));
        assertThat(first.createdAt()).isNotNull();
        long created1 = first.createdAt();
        long modified1 = first.updatedAt();

        Instant before2 = Instant.now();
        RecBook second = ops.save(first);
        Instant after2 = Instant.now();

        assertThat(second.createdAt()).isEqualTo(created1);
        assertThat(second.updatedAt()).isBetween(before2.toEpochMilli(), after2.toEpochMilli());
        assertThat(second.updatedAt()).isGreaterThanOrEqualTo(modified1);
    }

    @Test
    @DisplayName("record returns a new instance with every non-audit component equal; an audit-free entity returns the same instance with no extra document fields")
    void recordRebuiltOthersPreservedPlainUnaffected() {
        RecBook input = new RecBook(6L, "基地", null, 0L);
        RecBook saved = ops.save(input);
        assertThat(saved).isNotSameAs(input);
        assertThat(saved.id()).isEqualTo(input.id());
        assertThat(saved.title()).isEqualTo(input.title());

        PlainBook plain = new PlainBook(7L, "x");
        PlainBook savedPlain = ops.save(plain);
        assertThat(savedPlain).isSameAs(plain);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gw).updateDocuments(eq("audit_plain"), body.capture());
        assertThat(body.getValue()).isEqualTo("{\"id\":7,\"title\":\"x\"}");
    }

    @Test
    @DisplayName("BeforeConvertCallback sees the entity already audit-filled, and the serialized values match what the callback observed")
    void beforeConvertSeesFilledEntity() {
        var cbs = new MeiliEntityCallbacks();
        final PojoBook[] seen = new PojoBook[1];
        cbs.register(PojoBook.class, (BeforeConvertCallback<PojoBook>) (e, i) -> {
            seen[0] = e;
            return e;
        });
        var opsCb = new DefaultMeiliSearchOperations(gw, new MeiliMappingContext(), SER,
                cbs, false, Duration.ofSeconds(5));

        opsCb.save(new PojoBook(8L, "c"));

        assertThat(seen[0]).isNotNull();
        assertThat(seen[0].createdAt).isNotNull();
        assertThat(seen[0].updatedAt).isNotNull();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gw).updateDocuments(eq("audit_pojo"), body.capture());
        // the serialized value matches the filled value observed by the callback
        assertThat(OffsetDateTime.parse(parse(body.getValue()).get("createdAt").asText()))
                .isEqualTo(seen[0].createdAt);
    }

    @Test
    @DisplayName("saveAll fills each entity independently: every returned instance carries its own timestamps")
    void saveAllFillsEachEntity() {
        List<RecBook> saved = ops.saveAll(List.of(
                new RecBook(9L, "a", null, 0L), new RecBook(10L, "b", null, 0L)));
        assertThat(saved).hasSize(2);
        for (RecBook r : saved) {
            assertThat(r.createdAt()).isNotNull().isNotZero();
            assertThat(r.updatedAt()).isNotZero();
        }
        assertThat(saved.get(0)).isNotSameAs(saved.get(1));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gw).updateDocuments(eq("audit_rec"), body.capture());
        JsonNode arr = parse(body.getValue());
        assertThat(arr).hasSize(2);
        assertThat(arr.get(0).get("createdAt").isNull()).isFalse();
        assertThat(arr.get(1).get("createdAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("Read path never touches audit fields: findById returns the stored old timestamps verbatim, with no write request")
    void findByIdKeepsStoredAuditValues() {
        when(gw.fetchRawDocument("audit_pojo", "1")).thenReturn(Optional.of(
                "{\"id\":1,\"title\":\"a\",\"createdAt\":\"2000-01-01T00:00:00Z\","
                        + "\"updatedAt\":\"2000-01-02T00:00:00Z\"}"));
        PojoBook b = ops.findById(1L, PojoBook.class).orElseThrow();
        assertThat(b.createdAt).isEqualTo(OffsetDateTime.parse("2000-01-01T00:00:00Z"));
        assertThat(b.updatedAt).isEqualTo(OffsetDateTime.parse("2000-01-02T00:00:00Z"));
        verify(gw, never()).updateDocuments(any(), any());
    }

    @Test
    @DisplayName("deleteById never touches audit fields: only the delete channel runs, no document write")
    void deleteByIdNeverWritesDocuments() {
        when(gw.deleteDocument("audit_pojo", "1")).thenReturn(2);
        ops.deleteById(1L, PojoBook.class);
        verify(gw).deleteDocument("audit_pojo", "1");
        verify(gw, never()).updateDocuments(any(), any());
    }

    /**
     * Parses a captured document body, failing the test on malformed JSON.
     *
     * @param json captured request body
     * @return parsed tree
     */
    private static JsonNode parse(String json) {
        try {
            return M.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("invalid JSON: " + json, e);
        }
    }
}
