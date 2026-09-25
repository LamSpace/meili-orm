package io.github.lamspace.meili.it;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * 矩阵往返实体：超 2^53 的 Long 主键 + 角色投影（searchable/filterable/sortable）。
 *
 * <p>启动期 initializer（sync-settings + apply）即按此投影建索引推 settings，
 * 随后 CRUD 与搜索往返全链路覆盖 Long 精度契约。record 形态与 POJO 形态之一由本类承担。
 */
@MeiliDocument(indexName = "it_starter_books")
public record ITBook(
        @MeiliId Long id,
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
        @MeiliField(filterable = true, sortable = true) Double price) {
}
