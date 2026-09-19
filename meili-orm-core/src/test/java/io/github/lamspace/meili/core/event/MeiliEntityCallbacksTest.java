package io.github.lamspace.meili.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MeiliEntityCallbacks} 匹配、顺序与注册校验契约测试。 */
class MeiliEntityCallbacksTest {

    record Note(Long id, String text) {}

    static class SuperNote {}

    static class Child extends SuperNote {}

    @Test
    @DisplayName("匿名类经反射解析泛型实参；同类型多回调按注册顺序链式执行")
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
    @DisplayName("父类型回调对子类型实体生效")
    void superclassCallbackApplies() {
        var cbs = new MeiliEntityCallbacks();
        cbs.register(SuperNote.class, (BeforeConvertCallback<SuperNote>) (e, idx) -> e);
        assertThat(cbs.<SuperNote>onBeforeConvert(new Child(), "c")).isNotNull();
    }

    @Test
    @DisplayName("AfterLoad 以改写后的 raw JSON 传递到反序列化前")
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
    @DisplayName("泛型实参不可解析的注册（raw 实现类）当场拒绝")
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
    @DisplayName("none() 空注册表全点透传且计数为零")
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
