package io.github.lamspace.meili.example.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliSetting;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 演示实体：一页覆盖全部映射面。
 *
 * <p>角色声明即 settings 投影（不标注=不声明）：{@code book_title}/{@code overview}
 * 进 searchableAttributes（显式 order 在前）、{@code author.city}/{@code tags}/
 * {@code genre}/{@code price} 进 filterableAttributes（嵌套点路径展平）、
 * {@code price}/{@code publishedAt} 进 sortableAttributes。rankingRules 与中文
 * stopwords 由 {@code meili/books.json} 透传，与投影合并时透传优先。
 * {@code internalNote} 经 {@code @JsonIgnore} 整体不落文档。
 *
 * @param id             主键（Long，超 2^53 探针值验证无损）
 * @param title          书名，文档字段名 book_title，搜索权重第一
 * @param overview       简介，次级搜索字段
 * @param author         作者嵌套对象（city 参与 filterable 投影）
 * @param tags           标签数组，filterable
 * @param genre          题材，filterable 兼 facet 演示
 * @param price          定价，filterable + sortable
 * @param publishedAt    首发时间，sortable
 * @param internalNote   内部备注，不写入 MeiliSearch 文档
 */
@MeiliDocument(indexName = "books")
@MeiliSetting(settingPath = "classpath:meili/books.json")
public record Book(
        @MeiliId Long id,
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
        @MeiliField(searchable = true) String overview,
        Author author,
        @MeiliField(filterable = true) List<String> tags,
        @MeiliField(filterable = true) String genre,
        @MeiliField(filterable = true, sortable = true) Double price,
        @MeiliField(sortable = true) OffsetDateTime publishedAt,
        @JsonIgnore String internalNote) {
}
