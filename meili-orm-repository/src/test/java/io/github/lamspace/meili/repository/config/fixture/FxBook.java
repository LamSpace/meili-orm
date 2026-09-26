package io.github.lamspace.meili.repository.config.fixture;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;

/** 注册器测试夹具实体。 */
@MeiliDocument(indexName = "fx_books")
public class FxBook {
    /** 主键。 */
    @MeiliId
    public Long id;
    /** 标题。 */
    public String title;
}
