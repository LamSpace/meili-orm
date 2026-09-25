package io.github.lamspace.meili.serialize.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Jackson3DocumentSerializer} 行为契约测试。
 *
 * <p>与 core 的 Jackson2 实现测试镜像同构断言集（精度、改名、日期、宽容读、base
 * 配置尊重、异常包装）：序列化后端可替换的前提是行为逐项等价，故这里移植断言而非
 * 另起炉灶。注解仍来自 com.fasterxml.jackson.annotation——Jackson 3 有意保留该
 * 坐标为注解事实源。
 */
class Jackson3DocumentSerializerTest {

    private final Jackson3DocumentSerializer s = new Jackson3DocumentSerializer(new JsonMapper());

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
        ObjectMapper m = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        record Item(@MeiliId Long id, BigDecimal unitPrice) {}
        assertThat(new Jackson3DocumentSerializer(m).write(new Item(1L, new BigDecimal("9.9"))))
                .contains("unit_price");
    }

    @Test
    @DisplayName("构造不污染 base mapper：注册后 base 仍可独立使用且无 meili 桥接")
    void baseMapperNotMutated() {
        ObjectMapper base = new JsonMapper();
        Jackson3DocumentSerializer copy = new Jackson3DocumentSerializer(base);
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
