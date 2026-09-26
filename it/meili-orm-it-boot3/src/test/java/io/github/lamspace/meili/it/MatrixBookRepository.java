package io.github.lamspace.meili.it;

import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.MeiliQuery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

/** 矩阵仓库夹具：派生、全文、分页、注解查询各一路。 */
public interface MatrixBookRepository extends MeiliRepository<MatrixBook, Long> {

    /**
     * @param genre 分类
     * @return 命中文档
     */
    List<MatrixBook> findByGenre(String genre);

    /**
     * @param t 全文片段
     * @return 命中文档
     */
    List<MatrixBook> findByTitleContaining(String t);

    /**
     * @param genre 分类
     * @param pg    分页与排序载体
     * @return 一页结果
     */
    Page<MatrixBook> findPageByGenreOrderByPriceAsc(String genre, Pageable pg);

    /**
     * @param min 价格下界
     * @return 命中文档
     */
    @MeiliQuery(filter = "price > :min")
    List<MatrixBook> expensive(@Param("min") Double min);
}
