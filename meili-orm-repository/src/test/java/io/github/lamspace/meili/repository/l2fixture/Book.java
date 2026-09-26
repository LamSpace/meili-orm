package io.github.lamspace.meili.repository.l2fixture;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;

/** L2 装配测试夹具实体。 */
@MeiliDocument(indexName = "l2_books")
public class Book {
    /** 主键。 */
    @MeiliId
    public Long id;
    /** 标题。 */
    public String title;
}
