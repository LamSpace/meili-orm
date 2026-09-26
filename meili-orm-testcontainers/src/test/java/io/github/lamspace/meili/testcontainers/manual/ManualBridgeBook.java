package io.github.lamspace.meili.testcontainers.manual;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * 手工桥接样例的往返实体：独立索引，避免与服务连接场景的索引互相污染。
 */
@MeiliDocument(indexName = "tc_manual_bridge_books")
record ManualBridgeBook(
        @MeiliId Long id,
        @MeiliField(name = "title", searchable = true, searchableOrder = 1) String title) {
}
