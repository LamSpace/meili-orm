package io.github.lamspace.meili.repository.spike;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/**
 * spike 样本实体（record 形态）：验证结构桥按字段解析 record 组件。
 *
 * @param id    主键
 * @param title 改名属性
 * @param genre filterable 属性
 */
@MeiliDocument(indexName = "spike_record_books")
record SpikeRecordBook(
        @MeiliId Long id,
        @MeiliField(name = "record_title", searchable = true) String title,
        @MeiliField(filterable = true) String genre) {
}
