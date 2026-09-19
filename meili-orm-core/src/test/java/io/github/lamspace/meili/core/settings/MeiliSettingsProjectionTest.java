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

/** {@link MeiliSettingsProjection} 角色投影、透传合并与格式稳定性契约测试。 */
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
    @DisplayName("仅声明角色生成数组：显式 order 在前，其余按字典序稳定排列")
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
    @DisplayName("不标注 = 不声明：无任何角色时四数组皆 null 且 hasAny=false")
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
    @DisplayName("输出格式逐字节锁定（golden 文件），变更格式 = 显式改 golden")
    void goldenFileDiff() {
        var p = projection.project(MeiliPersistentEntity.of(Book.class));
        String golden = resource("/golden/books-settings.json").trim();
        assertThat(p.toJson().trim()).isEqualTo(golden);
    }

    @Test
    @DisplayName("透传键合并进投影且透传优先（覆盖同名数组），嵌套对象原样保留")
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
        assertThat(p.searchableAttributes()).containsExactly("t"); // 字段保留投影值

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
    @DisplayName("透传未知键拒绝：消息含非法键名与来源文件")
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
