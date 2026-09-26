package io.github.lamspace.meili.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MeiliPersistentEntity} 解析与启动期校验行为契约测试。 */
class MeiliPersistentEntityTest {

    @MeiliDocument(indexName = "books")
    @MeiliSetting(settingPath = "classpath:meili/books.json")
    @MeiliSetting(settingPath = "classpath:meili/books-rank.json")
    static class Book {
        @MeiliId Long id;
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1)
        String title;
        Author author;
        String genre;
        @MeiliField(filterable = true, sortable = true)
        Double price;
        @JsonIgnore String secret;
        static String staticField = "x";
    }

    static class Author {
        @MeiliField(filterable = true) String city;
        String name;
    }

    @Test
    @DisplayName("解析注解角色、改名点路径与透传声明，属性按 jsonPath 稳定排序")
    void parsesFlagsAndPaths() {
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Book.class);

        assertThat(e.getType()).isEqualTo(Book.class);
        assertThat(e.getIndexName()).isEqualTo("books");
        assertThat(e.getSettingPaths()).containsExactly(
                "classpath:meili/books.json", "classpath:meili/books-rank.json");
        assertThat(e.getIdProperty().getJsonPath()).isEqualTo("id");
        assertThat(e.getIdProperty().isId()).isTrue();

        assertThat(e.getProperties()).extracting(MeiliPersistentProperty::getJsonPath)
                .containsExactly("author.city", "author.name", "book_title", "genre", "id", "price");

        MeiliPersistentProperty price = property(e, "price");
        assertThat(price.isFilterable()).isTrue();
        assertThat(price.isSortable()).isTrue();
        assertThat(price.isSearchable()).isFalse();
        assertThat(price.getSearchableOrder()).isEqualTo(-1);

        assertThat(property(e, "author.city").isFilterable()).isTrue(); // 嵌套展平点路径

        MeiliPersistentProperty title = property(e, "book_title"); // @MeiliField.name 改名即投影名
        assertThat(title.isSearchable()).isTrue();
        assertThat(title.getSearchableOrder()).isEqualTo(1);

        // @JsonIgnore 与 static 字段被排除；未标注字段仅以投影名存在、无任何角色
        assertThat(e.getProperties()).noneMatch(p -> p.getJsonPath().equals("secret"));
        assertThat(e.getProperties()).noneMatch(p -> p.getJsonPath().equals("staticField"));
        assertThat(property(e, "genre").isFilterable()).isFalse();
    }

    @MeiliDocument(indexName = "bookrecords")
    record AnnotatedBookRecord(@MeiliId Long id, @MeiliField(searchable = true) String title) {}

    @Test
    @DisplayName("record 形态：组件注解经字段传播生效，主键访问器走组件访问方法")
    void recordComponentsWithDocument() {
        MeiliPersistentEntity e = MeiliPersistentEntity.of(AnnotatedBookRecord.class);
        assertThat(e.getIdProperty().getJsonPath()).isEqualTo("id");
        assertThat(e.getIndexName()).isEqualTo("bookrecords");
        assertThat(property(e, "title").isSearchable()).isTrue();
        assertThat(e.idValue(new AnnotatedBookRecord(9007199254740993L, "三体")))
                .isEqualTo(9007199254740993L);
    }

    @Test
    @DisplayName("idValue 三通道：显式 getter 优先，其次 record 访问器，字段回退")
    void idValueResolutionOrder() {
        MeiliPersistentEntity viaGetter = MeiliPersistentEntity.of(GetterId.class);
        assertThat(viaGetter.idValue(new GetterId(42L))).isEqualTo(42L);

        MeiliPersistentEntity viaField = MeiliPersistentEntity.of(Book.class);
        Book b = new Book();
        b.id = 7L;
        assertThat(viaField.idValue(b)).isEqualTo(7L);
    }

    @MeiliDocument(indexName = "g")
    static class GetterId {
        @MeiliId Long identifier;
        GetterId(Long identifier) { this.identifier = identifier; }
        public Long getIdentifier() { return identifier; }
    }

    @Test
    void requiresId() {
        @MeiliDocument(indexName = "x") class NoId { String x; }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(NoId.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("@MeiliId")
                .hasMessageContaining(NoId.class.getName());
    }

    @Test
    void duplicateIdRejected() {
        @MeiliDocument(indexName = "x") class TwoIds { @MeiliId Long a; @MeiliId String b; }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(TwoIds.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("@MeiliId");
    }

    @Test
    void requiresDocument() {
        class Bare { @MeiliId Long id; }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Bare.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("@MeiliDocument");
    }

    @Test
    void blankIndexNameRejected() {
        @MeiliDocument(indexName = "  ") class Blank { @MeiliId Long id; }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Blank.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("indexName");
    }

    @Test
    void idTypeMustBeStringOrInteger() {
        @MeiliDocument(indexName = "x") class BadId { @MeiliId Double id; }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(BadId.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("主键")
                .hasMessageContaining("Double");
    }

    @Test
    void renameConflictWithJsonPropertyThrows() {
        @MeiliDocument(indexName = "x") class Conflict {
            @MeiliId Long id;
            @MeiliField(name = "a") @JsonProperty("b") String title;
        }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Conflict.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("a")
                .hasMessageContaining("b")
                .hasMessageContaining("title");
    }

    @Test
    void jsonPropertyAloneBecomesProjectionName() {
        @MeiliDocument(indexName = "x") class Jp {
            @MeiliId Long id;
            @JsonProperty("renamed") String original;
        }
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Jp.class);
        assertThat(e.getProperties()).extracting(MeiliPersistentProperty::getJsonPath)
                .contains("renamed");
    }

    @Test
    void cyclicNestingTerminates() {
        @MeiliDocument(indexName = "n") class Node {
            @MeiliId Long id;
            Node next;
        }
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Node.class);
        assertThat(e.getProperties()).isNotEmpty();
        assertThat(e.getProperties()).extracting(MeiliPersistentProperty::getJsonPath)
                .containsExactly("id", "next"); // 环引用字段止步为叶子，不再展开
    }

    @Test
    @DisplayName("嵌套展平深度上限 3：第 4 层对象止步为叶子属性")
    void nestingDepthLimited() {
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Depth1.class);
        List<String> paths = e.getProperties().stream()
                .map(MeiliPersistentProperty::getJsonPath).toList();
        assertThat(paths).contains("b.c.d");
        assertThat(paths).doesNotContain("b.c.d.val");
    }

    @MeiliDocument(indexName = "d")
    static class Depth1 { @MeiliId Long id; Depth2 b; }
    static class Depth2 { Depth3 c; }
    static class Depth3 { Depth4 d; }
    static class Depth4 { String val; }

    @Test
    @DisplayName("角色注解声明在将展开的容器字段上 → fail-fast 指向叶子")
    void roleOnContainerFieldRejected() {
        @MeiliDocument(indexName = "x") class Bad {
            @MeiliId Long id;
            @MeiliField(filterable = true) Author author;
        }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Bad.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("author");
    }

    @Test
    @DisplayName("searchableOrder 重复 → 拒绝（排序不得依赖遍历巧合）")
    void duplicateSearchableOrderRejected() {
        @MeiliDocument(indexName = "x") class Dup {
            @MeiliId Long id;
            @MeiliField(searchable = true, searchableOrder = 1) String a;
            @MeiliField(searchable = true, searchableOrder = 1) String b;
        }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Dup.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("searchableOrder");
    }

    @Test
    @DisplayName("集合/数组为不透明叶子：不递归泛型参数")
    void collectionsAreOpaqueLeaves() {
        @MeiliDocument(indexName = "x") class Bag {
            @MeiliId Long id;
            java.util.List<Author> authors;
        }
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Bag.class);
        assertThat(e.getProperties()).extracting(MeiliPersistentProperty::getJsonPath)
                .containsExactly("authors", "id");
        assertThat(property(e, "authors").isFilterable()).isFalse();
    }

    @Test
    @DisplayName("审计注解解析为元模型标记，六类许可类型全部通过")
    void auditFlagsParsed() {
        @MeiliDocument(indexName = "a") class Audited {
            @MeiliId Long id;
            @CreatedDate OffsetDateTime createdAt;
            @LastModifiedDate long updatedAt;
        }
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Audited.class);
        assertThat(property(e, "createdAt").isCreatedDate()).isTrue();
        assertThat(property(e, "createdAt").isLastModifiedDate()).isFalse();
        assertThat(property(e, "updatedAt").isLastModifiedDate()).isTrue();
        assertThat(property(e, "updatedAt").isCreatedDate()).isFalse();
        assertThat(e.getCreatedDateFields()).extracting(Field::getName).containsExactly("createdAt");
        assertThat(e.getLastModifiedDateFields()).extracting(Field::getName)
                .containsExactly("updatedAt");
        assertThat(e.hasAuditFields()).isTrue();
    }

    @MeiliDocument(indexName = "audit_six")
    static class AuditSixTypes {
        @MeiliId Long id;
        @CreatedDate Instant a;
        @CreatedDate OffsetDateTime b;
        @CreatedDate ZonedDateTime c;
        @LastModifiedDate LocalDateTime d;
        @LastModifiedDate long e;
        @LastModifiedDate Long f;
    }

    @Test
    @DisplayName("许可集六类型（Instant/OffsetDateTime/ZonedDateTime/LocalDateTime/long/Long）解析成功")
    void auditAllowedTypesParse() {
        MeiliPersistentEntity e = MeiliPersistentEntity.of(AuditSixTypes.class);
        assertThat(e.getProperties()).hasSize(7);
        assertThat(property(e, "a").isCreatedDate()).isTrue();
        assertThat(property(e, "f").isLastModifiedDate()).isTrue();
    }

    @Test
    @DisplayName("@CreatedDate String → 解析期 fail-fast，消息含类名与字段名")
    void auditStringTypeRejected() {
        @MeiliDocument(indexName = "x") class Bad {
            @MeiliId Long id;
            @CreatedDate String createdAt;
        }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Bad.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining(Bad.class.getName())
                .hasMessageContaining("createdAt");
    }

    @Test
    @DisplayName("@LastModifiedDate String → 同样 fail-fast")
    void lastModifiedStringTypeRejected() {
        @MeiliDocument(indexName = "x") class Bad {
            @MeiliId Long id;
            @LastModifiedDate String updatedAt;
        }
        assertThatThrownBy(() -> MeiliPersistentEntity.of(Bad.class))
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining(Bad.class.getName())
                .hasMessageContaining("updatedAt");
    }

    @Test
    @DisplayName("审计标记与 @MeiliField 角色标注共现互不干扰")
    void auditCoexistsWithRoles() {
        @MeiliDocument(indexName = "x") class Both {
            @MeiliId Long id;
            @MeiliField(searchable = true, filterable = true)
            @CreatedDate OffsetDateTime createdAt;
        }
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Both.class);
        MeiliPersistentProperty p = property(e, "createdAt");
        assertThat(p.isCreatedDate()).isTrue();
        assertThat(p.isSearchable()).isTrue();
        assertThat(p.isFilterable()).isTrue();
        assertThat(p.getSearchableOrder()).isEqualTo(-1);
    }

    @Test
    @DisplayName("无审计实体回归不变：所有属性审计标记为 false，hasAuditFields 为 false")
    void nonAuditEntityUnaffected() {
        MeiliPersistentEntity e = MeiliPersistentEntity.of(Book.class);
        assertThat(e.hasAuditFields()).isFalse();
        assertThat(e.getProperties()).allSatisfy(p -> {
            assertThat(p.isCreatedDate()).isFalse();
            assertThat(p.isLastModifiedDate()).isFalse();
        });
    }

    private static MeiliPersistentProperty property(MeiliPersistentEntity e, String jsonPath) {
        return e.getProperties().stream()
                .filter(p -> p.getJsonPath().equals(jsonPath)).findFirst().orElseThrow();
    }
}
