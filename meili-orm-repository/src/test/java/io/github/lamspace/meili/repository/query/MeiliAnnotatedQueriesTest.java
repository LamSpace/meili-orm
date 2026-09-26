package io.github.lamspace.meili.repository.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.core.MeiliRepositoryProxy;
import io.github.lamspace.meili.repository.query.MeiliDerivedQueriesTest.DqBook;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

/**
 * L1：{@code @MeiliQuery} 契约——模板组合、双轨绑定、filter 字面量转义（注入面）、
 * 启动期占位符/括号校验、distinct 直通、与派生短路的 WARN 与排序/top 保留。
 */
class MeiliAnnotatedQueriesTest {

    /** 注解查询夹具仓库（复用派生测试实体）。 */
    public interface AnnRepo extends MeiliRepository<DqBook, Long> {

        @io.github.lamspace.meili.repository.MeiliQuery(q = ":text", filter = "genre = :g")
        List<DqBook> combo(@Param("text") String text, @Param("g") String genre);

        @io.github.lamspace.meili.repository.MeiliQuery(filter = "price > ?0 AND genre = :g")
        List<DqBook> positional(Double min, @Param("g") String genre);

        @io.github.lamspace.meili.repository.MeiliQuery(filter = "genre = :g")
        List<DqBook> injection(@Param("g") String evil);

        @io.github.lamspace.meili.repository.MeiliQuery(q = "#{#name.toUpperCase()}")
        List<DqBook> spel(String name);

        @io.github.lamspace.meili.repository.MeiliQuery(filter = "genre = :g", distinct = "author.city")
        List<DqBook> dedup(@Param("g") String g);

        @io.github.lamspace.meili.repository.MeiliQuery(filter = "active = true")
        List<DqBook> findByPriceGreaterThan(Double ignored, Pageable pg);

    }

    MeiliSearchOperations ops;
    AnnRepo repo;

    @BeforeEach
    void setUp() {
        ops = mock(MeiliSearchOperations.class);
        org.mockito.Mockito.doReturn(MeiliSearchResult.from("{\"hits\":[]}", DqBook.class,
                        new Jackson2DocumentSerializer(new ObjectMapper())))
                .when(ops).search(any(MeiliQuery.class), any());
        repo = (AnnRepo) MeiliRepositoryProxy.create(AnnRepo.class, ops, new MeiliMappingContext());
    }

    private MeiliQuery captured() {
        ArgumentCaptor<MeiliQuery> cap = ArgumentCaptor.forClass(MeiliQuery.class);
        verify(ops).search(cap.capture(), any());
        return cap.getValue();
    }

    @Test
    void qAndFilterCombine() {
        repo.combo("三体", "科幻");
        MeiliQuery q = captured();
        assertThat(q.getQ()).isEqualTo("三体");
        assertThat(q.getFilterDsl()).isEqualTo("genre = \"科幻\"");
    }

    @Test
    void positionalAndNamedMixed() {
        repo.positional(30.0, "科幻");
        assertThat(captured().getFilterDsl()).isEqualTo("price > 30.0 AND genre = \"科幻\"");
    }

    @Test
    @DisplayName("注入面：恶意字符串整体成为被转义的字面量，DSL 结构不变")
    void injectionEscaped() {
        repo.injection("科幻\" OR price > 0 --");
        assertThat(captured().getFilterDsl())
                .isEqualTo("genre = \"科幻\\\" OR price > 0 --\"");
    }

    @Test
    void spelExpressionEvaluates() {
        repo.spel("x");
        assertThat(captured().getQ()).isEqualTo("X");
    }

    @Test
    void distinctPassesThrough() {
        repo.dedup("科幻");
        assertThat(captured().getDistinct()).isEqualTo("author.city");
    }

    @Test
    @DisplayName("注解短路方法名条件：OrderBy/top 仍来自方法名，其余忽略但分页参数生效")
    void precedenceKeepsOrderAndPageable() {
        repo.findByPriceGreaterThan(99.0, PageRequest.of(0, 10));
        MeiliQuery q = captured();
        assertThat(q.getFilterDsl()).isEqualTo("active = true");   // 注解为准
        assertThat(q.getPage()).isEqualTo(1);
        assertThat(q.getHitsPerPage()).isEqualTo(10);
    }

    @Test
    @DisplayName("SpEL 求值失败包装为 MeiliOrmException，含表达式与方法名")
    void spelFailureWrapped() {
        assertThatThrownBy(() -> repo.spel(null))
                .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliOrmException.class)
                .hasMessageContaining("#name.toUpperCase()");
    }

    @Test
    @DisplayName("启动期负向：空注解/未知占位符/括号不配对/?N 越界")
    void bootstrapValidation() {
        // empty()、badPlaceholder()、badBrackets()、badIndex() 任一存在即使整个接口构建失败，
        // 逐个接口的错误信息断言：
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(EmptyOnly.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("至少提供");
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadName.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadParen.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("括号不配对");
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadPos.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("?9");
    }

    /** 空注解接口。 */
    public interface EmptyOnly extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery
        List<DqBook> none();
    }

    /** 未知占位符接口。 */
    public interface BadName extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery(filter = "genre = :missing")
        List<DqBook> q(String g);
    }

    /** 括号不配对接口。 */
    public interface BadParen extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery(filter = "genre = :g AND (price > 1")
        List<DqBook> q(@Param("g") String g);
    }

    /** 越界位置接口。 */
    public interface BadPos extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery(filter = "price > ?9")
        List<DqBook> q(Double p);
    }
}
