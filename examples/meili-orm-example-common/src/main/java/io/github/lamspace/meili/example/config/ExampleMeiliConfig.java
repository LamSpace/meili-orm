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
package io.github.lamspace.meili.example.config;

import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.example.domain.Book;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Demo wiring: the practical shape of BeforeConvertCallback, one of the four lifecycle callbacks.
 *
 * <p>Declaring it as a bean lets meili-orm's auto-configured callback registry collect it and
 * trigger it before every write-path serialization — here it trims the book title's surrounding
 * whitespace, demonstrating how cross-cutting behavior is inserted without touching business
 * call sites.
 */
@Configuration(proxyBeanMethods = false)
public class ExampleMeiliConfig {

    /**
     * The configuration class is instantiated by the container; all behavior lives in the {@code @Bean} methods.
     */
    public ExampleMeiliConfig() {
    }

    /**
     * Pre-write callback bean: strips leading and trailing whitespace from the book title.
     *
     * <p>Declared as a named class rather than a lambda: the callback registry resolves the target
     * entity from the implementation class's generic signature, and a lambda instance's generic
     * type argument is erased by the JVM and unresolvable (see the limitations list).
     *
     * @return the before-convert callback acting on {@link Book}
     */
    @Bean
    BeforeConvertCallback<Book> trimBookTitle() {
        return new TrimBookTitle();
    }

    /** Title-trimming implementation: the record is immutable, so a matching title is rebuilt and returned. */
    static final class TrimBookTitle implements BeforeConvertCallback<Book> {

        /** Stateless implementation; the target entity type is carried by the class declaration's generic type argument. */
        TrimBookTitle() {
        }

        @Override
        public Book onBeforeConvert(Book entity, String indexName) {
            return entity.title() == null ? entity
                    : new Book(entity.id(), entity.title().strip(), entity.overview(),
                            entity.author(), entity.tags(), entity.genre(), entity.price(),
                            entity.publishedAt(), entity.internalNote());
        }
    }
}
