package io.github.lamspace.meili.repository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.repository.MeiliRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1：自持代理的分发契约（CRUD 委托 / default 方法 / Object 语义 / 未解析方法启动即错）
 * 与 {@link MeiliEntityInformation} 元数据桥。
 */
class MeiliRepositoryProxyTest {

    @MeiliDocument(indexName = "proxy_books")
    static class Book {
        @MeiliId
        Long id;
        String title;

        Book() {
        }

        Book(Long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    /** CRUD-only 仓库 + 一个 default 组合方法。 */
    public interface BookRepository extends MeiliRepository<Book, Long> {
        default String label(Book b) {
            return "book:" + b.id;
        }
    }

    /** 含未接入的自定义查询方法。 */
    public interface BadRepository extends MeiliRepository<Book, Long> {
        List<Book> findByTitle(String t);
    }

    MeiliSearchOperations ops;
    MeiliMappingContext context;
    BookRepository repo;

    @BeforeEach
    void setUp() {
        ops = mock(MeiliSearchOperations.class);
        context = new MeiliMappingContext();
        repo = (BookRepository) MeiliRepositoryProxy.create(BookRepository.class, ops, context);
    }

    @Test
    void crudMethodsDelegateToOperations() {
        Book b = new Book(1L, "三体");
        when(ops.save(b)).thenReturn(b);
        when(ops.findById(1L, Book.class)).thenReturn(Optional.of(b));
        when(ops.count(Book.class)).thenReturn(3L);
        assertThat(repo.save(b)).isSameAs(b);
        assertThat(repo.findById(1L)).contains(b);
        assertThat(repo.count()).isEqualTo(3L);
        repo.deleteById(9L);
        verify(ops).deleteById(9L, Book.class);
    }

    @Test
    void defaultMethodExecutesOnProxy() {
        assertThat(repo.label(new Book(7L, "x"))).isEqualTo("book:7");
    }

    @Test
    void objectMethodsFollowIdentitySemantics() {
        Object other = MeiliRepositoryProxy.create(BookRepository.class, ops, context);
        assertThat(repo).isNotEqualTo(other);
        assertThat(repo).isEqualTo(repo);
        assertThat(repo.hashCode()).isEqualTo(System.identityHashCode(repo));
        assertThat(repo.toString()).contains("MeiliRepositoryProxy");
    }

    @Test
    @DisplayName("自定义查询方法在构建期即完成解析：非法条件启动即错，不拖到首调")
    void customMethodValidatedAtBootstrap() {
        // BadRepository.findByTitle：title 无 filterable 角色 → 角色预检在 create() 期抛错
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadRepository.class, ops, context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("findByTitle")
                .hasMessageContaining("filterable");
    }

    @Test
    void unresolvableDomainTypeRejected() {
        // 域类型 Object 无 @MeiliDocument：元模型解析必须 fail-fast
        interface RawRepo extends MeiliRepository<Object, Object> {
        }
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(RawRepo.class, ops, context))
                .isInstanceOf(MeiliMappingException.class);
    }

    @Test
    void entityInformationBridgesCoreMetamodel() {
        MeiliEntityInformation<Book, Long> info =
                new MeiliEntityInformation<>(MeiliPersistentEntity.of(Book.class));
        assertThat(info.getJavaType()).isEqualTo(Book.class);
        assertThat(info.getIdType()).isEqualTo(Long.class);
        assertThat(info.getId(new Book(5L, "t"))).isEqualTo(5L);
        assertThat(info.isNew(new Book(null, "t"))).isTrue();
        assertThat(info.isNew(new Book(1L, "t"))).isFalse();
    }
}
