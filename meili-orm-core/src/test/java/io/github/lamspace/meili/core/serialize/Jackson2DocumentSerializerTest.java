package io.github.lamspace.meili.core.serialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link Jackson2DocumentSerializer} 行为契约测试：精度、改名一致、日期、宽容读、base 配置尊重。 */
class Jackson2DocumentSerializerTest {

    private final Jackson2DocumentSerializer s = new Jackson2DocumentSerializer(new ObjectMapper());

    record Doc(@MeiliId Long id, @MeiliField(name = "book_title", searchable = true) String title,
               @JsonIgnore String secret, OffsetDateTime publishedAt) {}

    static class PojoDoc {
        @MeiliId public Long id;
        @MeiliField(name = "book_title") public String title;
        @JsonIgnore public String secret;
        public PojoDoc() { }
        PojoDoc(Long id, String title, String secret) {
            this.id = id; this.title = title; this.secret = secret;
        }
    }

    @Test
    @DisplayName("Long 主键逐位往返 + 改名 + JsonIgnore 排除 + ISO 日期")
    void longPrecisionRoundTrip() {
        Doc d = new Doc(9007199254740993L, "三体", "hidden", OffsetDateTime.parse("2008-01-01T00:00:00Z"));
        String json = s.write(d);
        assertThat(json)
                .contains("\"book_title\"")
                .contains("9007199254740993")
                .doesNotContain("secret")
                .contains("2008-01-01T00:00:00Z");
        Doc back = s.read(json, Doc.class);
        assertThat(back.id()).isEqualTo(9007199254740993L);
        assertThat(back.title()).isEqualTo("三体");
    }

    @Test
    @DisplayName("POJO 形态：字段改名序列化与反序列化双向生效")
    void pojoRenameRoundTrip() {
        String json = s.write(new PojoDoc(1L, "活着", "hidden"));
        assertThat(json).contains("\"book_title\":\"活着\"").doesNotContain("hidden");
        PojoDoc back = s.read(json, PojoDoc.class);
        assertThat(back.title).isEqualTo("活着");
    }

    @Test
    @DisplayName("与映射层同名规则：@MeiliField.name 优先于 @JsonProperty（冲突裁决一致）")
    void meiliFieldBeatsJsonPropertyForSerialization() {
        class Conflict {
            @MeiliId Long id;
            @MeiliField(name = "a") @JsonProperty("b") String title;
            Conflict(Long id, String title) { this.id = id; this.title = title; }
        }
        assertThat(s.write(new Conflict(1L, "x"))).contains("\"a\":").doesNotContain("\"b\":");
    }

    @Test
    void unknownPropertiesIgnoredOnRead() {
        assertThat(s.read("{\"id\":1,\"title\":\"x\",\"zzz\":2}", Doc.class).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("base mapper 的命名策略被尊重：unitPrice → unit_price")
    void customBaseMapperRespected() {
        ObjectMapper m = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        record Item(@MeiliId Long id, BigDecimal unitPrice) {}
        assertThat(new Jackson2DocumentSerializer(m).write(new Item(1L, new BigDecimal("9.9"))))
                .contains("unit_price");
    }

    @Test
    @DisplayName("构造不污染 base mapper：注册后 base 仍可独立使用且无 meili 桥接")
    void baseMapperNotMutated() throws Exception {
        ObjectMapper base = new ObjectMapper();
        Jackson2DocumentSerializer copy = new Jackson2DocumentSerializer(base);
        copy.write(new Doc(1L, "x", null, null));
        // base 不含 meili 桥接：record 的 book_title 改名不生效
        assertThat(base.writeValueAsString(new Doc(1L, "x", null, null))).contains("\"title\"");
    }

    @Test
    void readFailureWrappedAsOrmException() {
        assertThatThrownBy(() -> s.read("{not json", Doc.class))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining(Doc.class.getName());
    }
}
