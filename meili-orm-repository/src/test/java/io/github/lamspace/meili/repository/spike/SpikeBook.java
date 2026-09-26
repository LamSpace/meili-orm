package io.github.lamspace.meili.repository.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * spike 样本实体（POJO 形态）：覆盖改名属性、嵌套点路径、集合不透明叶、
 * 布尔/时间属性与 {@code @JsonIgnore} 排除。
 */
@MeiliDocument(indexName = "spike_books")
class SpikeBook {

    /** 主键。 */
    @MeiliId
    Long id;
    /** 改名 + searchable。 */
    @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
    String title;
    /** searchable（叶）。 */
    @MeiliField(searchable = true)
    String overview;
    /** filterable。 */
    @MeiliField(filterable = true)
    String genre;
    /** filterable + sortable。 */
    @MeiliField(filterable = true, sortable = true)
    Double price;
    /** 嵌套聚合（展开为点路径）。 */
    SpikeAuthor author;
    /** 集合不透明叶（filterable）。 */
    @MeiliField(filterable = true)
    List<String> tags;
    /** 布尔属性（filterable）。 */
    @MeiliField(filterable = true)
    Boolean active;
    /** 时间属性（sortable）。 */
    @MeiliField(sortable = true)
    OffsetDateTime publishedAt;
    /** 被 Jackson 排除，不应参与查询解析。 */
    @JsonIgnore
    String secret;
}
