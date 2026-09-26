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
package io.github.lamspace.meili.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link MeiliMappingContext} caching and concurrency behavior. */
class MeiliMappingContextTest {

    @MeiliDocument(indexName = "a") record A(@MeiliId Long id) {}
    @MeiliDocument(indexName = "b") record B(@MeiliId Long id) {}

    @Test
    @DisplayName("Repeated lookups of the same Class return one cached instance; different Classes are independent")
    void cachesSameInstancePerType() {
        MeiliMappingContext ctx = new MeiliMappingContext();
        assertThat(ctx.getEntity(A.class)).isSameAs(ctx.getEntity(A.class));
        assertThat(ctx.getEntity(A.class)).isNotSameAs(ctx.getEntity(B.class));
    }

    @Test
    @DisplayName("Concurrent first access parses once (computeIfAbsent semantics); all threads share one instance")
    void concurrentFirstAccessSharesOneEntity() {
        MeiliMappingContext ctx = new MeiliMappingContext();
        long distinct = IntStream.range(0, 16).parallel()
                .mapToObj(i -> ctx.getEntity(A.class))
                .distinct().count();
        assertThat(distinct).isEqualTo(1);
    }

    @Test
    @DisplayName("Classes that fail parsing are not cached: an invalid entity throws a mapping exception on every lookup")
    void failedParseIsNotCached() {
        MeiliMappingContext ctx = new MeiliMappingContext();
        class Bad { String x; } // no @MeiliDocument and no @MeiliId
        assertThatThrownBy(() -> ctx.getEntity(Bad.class))
                .isInstanceOf(MeiliMappingException.class);
        assertThatThrownBy(() -> ctx.getEntity(Bad.class))
                .isInstanceOf(MeiliMappingException.class);
    }
}
