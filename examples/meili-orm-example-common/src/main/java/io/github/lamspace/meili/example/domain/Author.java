package io.github.lamspace.meili.example.domain;

import io.github.lamspace.meili.core.mapping.MeiliField;

/**
 * 嵌套值对象：演示点路径投影——{@code author.city} 作为 filterableAttributes 成员，
 * 与 MeiliSearch 服务端对嵌套对象的展平语义对齐。
 *
 * @param name 作者姓名（不参与任何角色数组）
 * @param city 作者所在城市，投影为 author.city 可过滤字段
 */
public record Author(String name, @MeiliField(filterable = true) String city) {
}
