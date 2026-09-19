package io.github.lamspace.meili.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MeiliMappingContext} 缓存与并发行为契约测试。 */
class MeiliMappingContextTest {

    @MeiliDocument(indexName = "a") record A(@MeiliId Long id) {}
    @MeiliDocument(indexName = "b") record B(@MeiliId Long id) {}

    @Test
    @DisplayName("同一 Class 重复获取返回同一缓存实例；不同 Class 相互独立")
    void cachesSameInstancePerType() {
        MeiliMappingContext ctx = new MeiliMappingContext();
        assertThat(ctx.getEntity(A.class)).isSameAs(ctx.getEntity(A.class));
        assertThat(ctx.getEntity(A.class)).isNotSameAs(ctx.getEntity(B.class));
    }

    @Test
    @DisplayName("并发首次访问只解析一次（computeIfAbsent 语义），全线程同实例")
    void concurrentFirstAccessSharesOneEntity() {
        MeiliMappingContext ctx = new MeiliMappingContext();
        long distinct = IntStream.range(0, 16).parallel()
                .mapToObj(i -> ctx.getEntity(A.class))
                .distinct().count();
        assertThat(distinct).isEqualTo(1);
    }

    @Test
    @DisplayName("解析失败的类不被缓存：非法实体每次获取都抛映射异常")
    void failedParseIsNotCached() {
        MeiliMappingContext ctx = new MeiliMappingContext();
        class Bad { String x; } // 无 @MeiliDocument 且无 @MeiliId
        assertThatThrownBy(() -> ctx.getEntity(Bad.class))
                .isInstanceOf(MeiliMappingException.class);
        assertThatThrownBy(() -> ctx.getEntity(Bad.class))
                .isInstanceOf(MeiliMappingException.class);
    }
}
