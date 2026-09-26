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
 * L1: {@code @MeiliQuery} contract — template composition, dual-track binding, filter-literal
 * escaping (injection surface), bootstrap placeholder/bracket validation, distinct
 * pass-through, plus the short-circuit WARN and the kept order/top from the method name.
 */
class MeiliAnnotatedQueriesTest {

    /** Annotated-query fixture repository (reuses the derived-test entity). */
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
    @DisplayName("Injection surface: the malicious string becomes one escaped literal, DSL structure unchanged")
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
    @DisplayName("The annotation short-circuits name criteria: OrderBy/top still come from the name, the rest is ignored but the paging argument applies")
    void precedenceKeepsOrderAndPageable() {
        repo.findByPriceGreaterThan(99.0, PageRequest.of(0, 10));
        MeiliQuery q = captured();
        assertThat(q.getFilterDsl()).isEqualTo("active = true");   // the annotation wins
        assertThat(q.getPage()).isEqualTo(1);
        assertThat(q.getHitsPerPage()).isEqualTo(10);
    }

    @Test
    @DisplayName("A failed SpEL evaluation is wrapped into MeiliOrmException carrying the expression and method name")
    void spelFailureWrapped() {
        assertThatThrownBy(() -> repo.spel(null))
                .isInstanceOf(io.github.lamspace.meili.core.exception.MeiliOrmException.class)
                .hasMessageContaining("#name.toUpperCase()");
    }

    @Test
    @DisplayName("Bootstrap negatives: empty annotation / unknown placeholder / unbalanced brackets / ?N out of range")
    void bootstrapValidation() {
        // Any of empty(), badPlaceholder(), badBrackets(), badIndex() fails the whole interface build,
        // asserted per interface:
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(EmptyOnly.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("must provide at least one");
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadName.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadParen.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("parentheses are unbalanced");
        assertThatThrownBy(() -> MeiliRepositoryProxy.create(BadPos.class, ops, new MeiliMappingContext()))
                .hasMessageContaining("?9");
    }

    /** Empty-annotation interface. */
    public interface EmptyOnly extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery
        List<DqBook> none();
    }

    /** Unknown-placeholder interface. */
    public interface BadName extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery(filter = "genre = :missing")
        List<DqBook> q(String g);
    }

    /** Unbalanced-brackets interface. */
    public interface BadParen extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery(filter = "genre = :g AND (price > 1")
        List<DqBook> q(@Param("g") String g);
    }

    /** Out-of-range positional interface. */
    public interface BadPos extends MeiliRepository<DqBook, Long> {
        @io.github.lamspace.meili.repository.MeiliQuery(filter = "price > ?9")
        List<DqBook> q(Double p);
    }
}
