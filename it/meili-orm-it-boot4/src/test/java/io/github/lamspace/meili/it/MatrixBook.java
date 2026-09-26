package io.github.lamspace.meili.it;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;

/** 矩阵仓库用例实体。 */
@MeiliDocument(indexName = "matrix_repo_books")
public class MatrixBook {

    /** 主键（含 > 2^53 精度样本）。 */
    @MeiliId
    public Long id;
    /** 改名 + searchable。 */
    @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
    public String title;
    /** filterable。 */
    @MeiliField(filterable = true)
    public String genre;
    /** filterable + sortable。 */
    @MeiliField(filterable = true, sortable = true)
    public Double price;

    /** Jackson/反射用无参构造。 */
    public MatrixBook() {
    }

    /**
     * @param id    主键
     * @param title 书名
     * @param genre 分类
     * @param price 价格
     */
    public MatrixBook(Long id, String title, String genre, Double price) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.price = price;
    }
}
