/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * L2-lite: how {@link MeiliRepositoryFactoryBean} wires up in a bare Spring context — the
 * constructor argument drives the repository interface, operations/metamodel are pulled by
 * type, and missing dependencies fail fast.
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
            assertThat(first).isSameAs(second);          // FactoryBean singleton semantics
            assertThat(first.count()).isEqualTo(11L);
            assertThat(first.allBy()).isEmpty();         // default methods pass straight through
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
