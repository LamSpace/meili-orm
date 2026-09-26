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
 * 审计能力 L3 真机 IT（wait-task=true，v1.49.0）：save 填充的双时间戳落到服务端文档、
 * findById 回读与保存值逐位一致（Long 精度链路复用）、再 save 后 created 稳定且
 * modified 落于新时间窗。
 */
class MeiliAuditIT extends AbstractMeiliIntegrationTest {

    private static final ObjectMapper M = new ObjectMapper();

    @MeiliDocument(indexName = "m6audit_books")
    record AuditBook(@MeiliId Long id, String title,
                     @CreatedDate Long createdAt, @LastModifiedDate long updatedAt) {}

    @Test
    @DisplayName("真机链路：填充值入服务端文档→回读一致→再保存 created 稳定、modified 进新窗")
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

            // 服务端文档：双时间戳均已落盘且非空（raw GET 通道直查）
            JsonNode doc = M.readTree(gateway.fetchRawDocument("m6audit_books",
                    "9007199254740993").orElseThrow());
            assertThat(doc.get("createdAt").isNull()).isFalse();
            assertThat(doc.get("updatedAt").isNull()).isFalse();

            // findById 回读与保存值逐位一致（epoch 毫秒 Long 精度链路复用）
            AuditBook back = ops.findById(9007199254740993L, AuditBook.class).orElseThrow();
            assertThat(back.createdAt()).isEqualTo(saved.createdAt());
            assertThat(back.updatedAt()).isEqualTo(saved.updatedAt());

            // 再保存：created 同值保留，modified 落于第二次调用的时间窗
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
