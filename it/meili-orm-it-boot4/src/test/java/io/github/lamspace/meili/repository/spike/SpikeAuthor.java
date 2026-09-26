package io.github.lamspace.meili.repository.spike;

import io.github.lamspace.meili.core.mapping.MeiliField;

/** spike 样本嵌套类型：city 声明 filterable+sortable，name 无角色。 */
class SpikeAuthor {

    /** 嵌套点路径目标（author.city）。 */
    @MeiliField(filterable = true, sortable = true)
    String city;
    /** 无角色声明的嵌套属性。 */
    String name;
}
