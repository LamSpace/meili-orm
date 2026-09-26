package io.github.lamspace.meili.testcontainers;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * 桥接 IT 往返实体：Long 主键 + 标题，索引由启动期 create-if-missing 建立。
 */
@MeiliDocument(indexName = "tc_service_connection_books")
record BridgeBook(
        @MeiliId Long id,
        @MeiliField(name = "title", searchable = true, searchableOrder = 1) String title) {
}
