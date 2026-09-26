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
 * Behavior-contract tests for {@link Jackson3DocumentSerializer}.
 *
 * <p>Mirrors the assertion set of core's Jackson 2 implementation (precision, renames, dates,
 * lenient reads, base-configuration respect, exception wrapping): a swappable serialization
 * backend is only viable when its behavior is equivalent item-for-item, so the assertions are
 * ported rather than reinvented. Annotations still come from
 * com.fasterxml.jackson.annotation — Jackson 3 deliberately keeps that coordinate as the
 * source of truth for annotations.
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
    @DisplayName("Long primary key round-trips bit-for-bit + rename + JsonIgnore exclusion + ISO date")
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
    @DisplayName("POJO shape: field rename applies in both serialization and deserialization")
    void pojoRenameRoundTrip() {
        String json = s.write(new PojoDoc(1L, "活着", "hidden"));
        assertThat(json).contains("\"book_title\":\"活着\"").doesNotContain("hidden");
        PojoDoc back = s.read(json, PojoDoc.class);
        assertThat(back.title).isEqualTo("活着");
    }

    @Test
    @DisplayName("Same rule as the mapping layer: @MeiliField.name wins over @JsonProperty (consistent conflict)")
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
    @DisplayName("base mapper's naming strategy is respected: unitPrice → unit_price")
    void customBaseMapperRespected() {
        ObjectMapper m = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        record Item(@MeiliId Long id, BigDecimal unitPrice) {}
        assertThat(new Jackson3DocumentSerializer(m).write(new Item(1L, new BigDecimal("9.9"))))
                .contains("unit_price");
    }

    @Test
    @DisplayName("Construction does not pollute the base mapper: base stays usable without the meili bridge")
    void baseMapperNotMutated() {
        ObjectMapper base = new JsonMapper();
        Jackson3DocumentSerializer copy = new Jackson3DocumentSerializer(base);
        copy.write(new Doc(1L, "x", null, null));
        // base carries no meili bridge: the record's book_title rename does not apply
        assertThat(base.writeValueAsString(new Doc(1L, "x", null, null))).contains("\"title\"");
    }

    @Test
    void readFailureWrappedAsOrmException() {
        assertThatThrownBy(() -> s.read("{not json", Doc.class))
                .isInstanceOf(MeiliOrmException.class)
                .hasMessageContaining(Doc.class.getName());
    }
}
