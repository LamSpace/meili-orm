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
package io.github.lamspace.meili.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link MeiliEntityCallbacks} matching, ordering and registration validation. */
class MeiliEntityCallbacksTest {

    record Note(Long id, String text) {}

    static class SuperNote {}

    static class Child extends SuperNote {}

    @Test
    @DisplayName("Anonymous classes resolve generic arguments via reflection; multiple callbacks for one type chain in registration order")
    void appliesMatchingCallbacksInRegistrationOrder() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(new BeforeConvertCallback<Note>() {
            @Override
            public Note onBeforeConvert(Note entity, String indexName) {
                return new Note(entity.id(), entity.text() + "-1");
            }
        });
        cbs.register(Note.class, (BeforeConvertCallback<Note>) (e, idx) -> new Note(e.id(), e.text() + "-2"));
        assertThat(cbs.onBeforeConvert(new Note(1L, "x"), "notes").text()).isEqualTo("x-1-2");
        assertThat(cbs.registeredCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("A callback bound to a supertype applies to subtype entities")
    void superclassCallbackApplies() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(SuperNote.class, (BeforeConvertCallback<SuperNote>) (e, idx) -> e);
        assertThat(cbs.<SuperNote>onBeforeConvert(new Child(), "c")).isNotNull();
    }

    @Test
    @DisplayName("AfterLoad hands the rewritten raw JSON to deserialization")
    void afterLoadTransformsRawJson() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(Note.class, (AfterLoadCallback<Note>) (json, idx) -> json.replace("\"x\"", "\"y\""));
        assertThat(cbs.onAfterLoad("{\"text\":\"x\"}", Note.class, "notes")).contains("\"y\"");
    }

    @Test
    void nonMatchingTypeSkipped() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(String.class, (BeforeConvertCallback<String>) (e, idx) -> e + "!");
        assertThat(cbs.onBeforeConvert(new Note(1L, "t"), "notes").text()).isEqualTo("t");
        assertThat(cbs.registeredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Registration with an unresolvable generic argument (raw implementation class) is rejected on the spot")
    void unresolvedGenericRejectedAtRegistration() {
        MeiliCallback raw = new RawConverter();
        assertThatThrownBy(() -> new MeiliEntityCallbacks().register(raw))
                .isInstanceOf(MeiliMappingException.class);
    }

    static class RawConverter implements BeforeConvertCallback {
        @Override
        public Object onBeforeConvert(Object entity, String indexName) {
            return entity;
        }
    }

    @Test
    @DisplayName("none() empty registry passes every hook through with a zero count")
    void nonePassesThrough() {
        var cbs = MeiliEntityCallbacks.none();
        Note n = new Note(1L, "t");
        assertThat(cbs.onBeforeConvert(n, "i")).isSameAs(n);
        assertThat(cbs.onAfterSave(n, "i")).isSameAs(n);
        assertThat(cbs.onAfterConvert(n, "i")).isSameAs(n);
        assertThat(cbs.onAfterLoad("{}", Note.class, "i")).isEqualTo("{}");
        assertThat(cbs.registeredCount()).isZero();
    }
}
