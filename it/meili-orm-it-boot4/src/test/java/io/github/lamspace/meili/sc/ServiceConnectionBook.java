package io.github.lamspace.meili.sc;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * 矩阵服务连接往返实体：索引由启动期 create-if-missing 建立，仅覆盖桥接装配路径。
 */
@MeiliDocument(indexName = "it_sc_books")
record ServiceConnectionBook(
        @MeiliId Long id,
        @MeiliField(name = "title", searchable = true, searchableOrder = 1) String title) {
}
