package io.github.lamspace.meili.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * L2-lite：{@link MeiliRepositoryFactoryBean} 在裸 Spring 上下文中的装配形态——
 * 构造参数驱动仓库接口、按类型拉取 operations/元模型、依赖缺失 fail-fast。
 */
class MeiliRepositoryFactoryBeanTest {

    @MeiliDocument(indexName = "fb_books")
    static class Book {
        @MeiliId
        Long id;
    }

    public interface BookRepository extends MeiliRepository<Book, Long> {
        default List<Book> allBy() {
            return List.of();
        }
    }

    private AnnotationConfigApplicationContext contextWith(boolean withOperations) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(MeiliMappingContext.class, MeiliMappingContext::new);
        if (withOperations) {
            MeiliSearchOperations ops = mock(MeiliSearchOperations.class);
            when(ops.count(Book.class)).thenReturn(11L);
            ctx.registerBean(MeiliSearchOperations.class, () -> ops);
        }
        RootBeanDefinition bd = new RootBeanDefinition(MeiliRepositoryFactoryBean.class);
        bd.getConstructorArgumentValues().addGenericArgumentValue(BookRepository.class);
        ctx.registerBeanDefinition("bookRepository", bd);
        return ctx;
    }

    @Test
    void factoryBeanBuildsUsableSingletonProxy() {
        try (AnnotationConfigApplicationContext ctx = contextWith(true)) {
            ctx.refresh();
            BookRepository first = ctx.getBean(BookRepository.class);
            BookRepository second = ctx.getBean(BookRepository.class);
            assertThat(first).isSameAs(second);          // FactoryBean 单例语义
            assertThat(first.count()).isEqualTo(11L);
            assertThat(first.allBy()).isEmpty();         // default 方法直通
        }
    }

    @Test
    void missingOperationsFailsFastWithActionableMessage() {
        try (AnnotationConfigApplicationContext ctx = contextWith(false)) {
            assertThatThrownBy(ctx::refresh)
                    .hasMessageContaining("MeiliSearchOperations");
        }
    }
}
