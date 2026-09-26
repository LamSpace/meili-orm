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
package io.github.lamspace.meili.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.mapping.MeiliSetting;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link MeiliSettingsProjection}: role projection, passthrough merging
 * and format stability.
 */
class MeiliSettingsProjectionTest {

    @MeiliDocument(indexName = "books")
    static class Book {
        @MeiliId Long id;
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title;
        @MeiliField(searchable = true) String overview;
        Author author;
        @MeiliField(filterable = true) String genre;
        @MeiliField(sortable = true, filterable = true) Double price;
        @MeiliField(displayed = true) String synopsis;
        String plainField;
    }

    static class Author {
        @MeiliField(filterable = true) String city;
    }

    private final MeiliSettingsProjection projection = new MeiliSettingsProjection();

    @Test
    @DisplayName("Only declared roles generate arrays: explicit order first, the rest in stable lexicographic order")
    void generatesOnlyDeclaredRolesInStableOrder() {
        var p = projection.project(MeiliPersistentEntity.of(Book.class));
        assertThat(p.searchableAttributes()).containsExactly("book_title", "overview");
        assertThat(p.filterableAttributes()).containsExactly("author.city", "genre", "price");
        assertThat(p.sortableAttributes()).containsExactly("price");
        assertThat(p.displayedAttributes()).containsExactly("synopsis");
        assertThat(p.passthrough()).isEmpty();
        assertThat(p.hasAny()).isTrue();
    }

    @Test
    @DisplayName("No annotation = no declaration: with no roles all four arrays are null and hasAny=false")
    void nothingDeclaredMeansNoArray() {
        @MeiliDocument(indexName = "x") class Bare {
            @MeiliId Long id;
            String a;
        }
        var p = projection.project(MeiliPersistentEntity.of(Bare.class));
        assertThat(p.searchableAttributes()).isNull();
        assertThat(p.filterableAttributes()).isNull();
        assertThat(p.sortableAttributes()).isNull();
        assertThat(p.displayedAttributes()).isNull();
        assertThat(p.hasAny()).isFalse();
        assertThat(p.toJson()).isEqualTo("{}");
    }

    @Test
    @DisplayName("Output format locked byte-for-byte (golden file); changing the format requires editing the golden explicitly")
    void goldenFileDiff() {
        var p = projection.project(MeiliPersistentEntity.of(Book.class));
        String golden = resource("/golden/books-settings.json").trim();
        assertThat(p.toJson().trim()).isEqualTo(golden);
    }

    @Test
    @DisplayName("Passthrough keys merge into the projection and win (overriding same-named arrays); nested objects kept verbatim")
    void passthroughMergesAndOverrides() {
        @MeiliDocument(indexName = "pt")
        @MeiliSetting(settingPath = "classpath:golden/passthrough.json")
        class Pt {
            @MeiliId Long id;
            @MeiliField(searchable = true, searchableOrder = 1) String t;
        }
        var p = projection.project(MeiliPersistentEntity.of(Pt.class));
        assertThat(p.passthrough()).containsEntry("rankingRules", List.of("words", "typo", "exactness"))
                .containsKey("stopWords");
        assertThat(p.searchableAttributes()).containsExactly("t"); // field keeps its projected value

        @MeiliDocument(indexName = "ov")
        @MeiliSetting(settingPath = "classpath:golden/passthrough-override.json")
        class Ov {
            @MeiliId Long id;
            @MeiliField(searchable = true, searchableOrder = 1) String t;
        }
        var overridden = projection.project(MeiliPersistentEntity.of(Ov.class));
        String json = overridden.toJson();
        assertThat(json).contains("custom_field").doesNotContain("\"t\"")
                .contains("faceting").contains("maxValuesPerFacet");
    }

    @Test
    @DisplayName("Unknown passthrough key rejected: message names the illegal key and its source file")
    void unknownPassthroughKeyRejected() {
        @MeiliDocument(indexName = "bad")
        @MeiliSetting(settingPath = "classpath:golden/unknown-key.json")
        class Bad {
            @MeiliId Long id;
        }
        assertThatThrownBy(() -> projection.project(MeiliPersistentEntity.of(Bad.class)))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("totallyBogus")
                .hasMessageContaining("unknown-key");
    }

    @Test
    void missingPassthroughFileRejected() {
        @MeiliDocument(indexName = "bad")
        @MeiliSetting(settingPath = "classpath:golden/nope.json")
        class Bad {
            @MeiliId Long id;
        }
        assertThatThrownBy(() -> projection.project(MeiliPersistentEntity.of(Bad.class)))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("golden/nope.json");
    }

    private static String resource(String path) {
        try (var in = MeiliSettingsProjectionTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
