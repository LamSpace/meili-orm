package io.github.lamspace.meili.example.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.example.domain.Book;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示 REST 面：导入 / 检索（q+filter+sort+分页+facet 全链路）/ 单读 / 删除 / raw 逃生舱。
 *
 * <p>业务代码只依赖 {@link MeiliSearchOperations} 与自有查询 IR，不触碰 SDK 类型——
 * 这正是 starter 的目标用户形态。预置数据反序列化用<b>显式自建</b>的 Jackson 2
 * mapper 而非注入容器 ObjectMapper：Boot 4 容器默认是 Jackson 3，注入会让 boot3
 * 壳与 boot4 壳行为分裂；显式 Jackson 2 保证两个壳读同一份 data.json 字节等价。
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    /** 预置数据专用 mapper：JSR-310 支持 + 宽容未知键。 */
    private static final ObjectMapper DATA_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** meili-orm 模板：全部场景经它走 raw 通道读写。 */
    private final MeiliSearchOperations operations;

    /**
     * 注入自动配置的 operations 模板。
     *
     * @param operations starter 装配的搜索/文档操作模板
     */
    public BookController(MeiliSearchOperations operations) {
        this.operations = operations;
    }

    /**
     * 批量导入预置书目（upsert 语义；wait-task=true 下返回即"写后可查"）。
     *
     * @return 导入条数回执
     * @throws java.io.IOException 预置数据读取失败
     */
    @PostMapping("/import")
    public Map<String, Object> importBooks() throws java.io.IOException {
        List<Book> books;
        try (var in = BookController.class.getResourceAsStream("/data.json")) {
            books = DATA_MAPPER.readValue(in, new TypeReference<List<Book>>() { });
        }
        operations.saveAll(books);
        return Map.of("imported", books.size());
    }

    /**
     * 全链路检索：全文 q + 等值/区间 filter + 排序 + 页码分页 + genre facet。
     *
     * @param q        全文查询词
     * @param genre    可选题材等值过滤
     * @param minPrice 可选价格下界过滤
     * @param sort     排序表达式，默认 price:asc
     * @param page     页码（1 起），默认 1
     * @param size     每页条数，默认 10
     * @return 命中、分页元数据与 facet 分布的响应体
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q,
                                      @RequestParam(required = false) String genre,
                                      @RequestParam(required = false) Double minPrice,
                                      @RequestParam(defaultValue = "price:asc") String sort,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        var query = MeiliQuery.query(q).sort(sort).page(page).hitsPerPage(size).facets("genre");
        if (genre != null) {
            query.filterAdd("genre = \"" + genre + "\"");
        }
        if (minPrice != null) {
            query.filterAdd("price > " + minPrice);
        }
        MeiliSearchResult<Book> result = operations.search(query, Book.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hits", result.getHits());
        body.put("estimatedTotalHits", result.getEstimatedTotalHits());
        body.put("page", result.getPage());
        body.put("hitsPerPage", result.getHitsPerPage());
        body.put("totalPages", result.getTotalPages());
        body.put("facetDistribution", result.getFacetDistribution());
        body.put("processingTimeMs", result.getProcessingTimeMs());
        return body;
    }

    /**
     * 按主键单读（raw 通道反序列化，Long 精度无损）。
     *
     * @param id 主键
     * @return 200 + 实体，或 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<Book> byId(@PathVariable Long id) {
        return operations.findById(id, Book.class)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 按主键删除（写后可查语义由 wait-task 承担）。
     *
     * @param id 主键
     * @return 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        operations.deleteById(id, Book.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * 逃生舱：服务端原始 JSON 原样透传，不经任何映射。
     *
     * @param q 全文查询词
     * @return 原始响应体
     */
    @GetMapping(value = "/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public String raw(@RequestParam String q) {
        return operations.search(q, Book.class).getRawJson();
    }
}
