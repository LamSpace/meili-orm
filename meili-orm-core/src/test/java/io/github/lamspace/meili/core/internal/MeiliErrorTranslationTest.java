package io.github.lamspace.meili.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.exceptions.APIError;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.exceptions.MeilisearchTimeoutException;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MeiliErrors#translate} 统一异常出口的契约测试。 */
class MeiliErrorTranslationTest {

    @Test
    @DisplayName("API 异常 → MeiliIndexAccessException 且服务端错误码可读")
    void apiExceptionBecomesIndexAccess() {
        var sdk = new MeilisearchApiException(
                new APIError().setCode("index_not_found").setMessage("no such index"));
        var t = MeiliErrors.translate(sdk);
        assertThat(t).isInstanceOf(MeiliIndexAccessException.class);
        assertThat(((MeiliIndexAccessException) t).getMeiliCode()).isEqualTo("index_not_found");
        assertThat(t).hasMessageContaining("no such index");
        assertThat(t.getCause()).isSameAs(sdk);
    }

    @Test
    @DisplayName("通信/超时类 SDK 异常 → 无错误码的访问异常（等待超时是另一条路）")
    void transportFailuresBecomeIndexAccessWithoutCode() {
        assertThat(MeiliErrors.translate(new MeilisearchTimeoutException("read timeout")))
                .isInstanceOf(MeiliIndexAccessException.class);
        assertThat(MeiliErrors.translate(new MeilisearchCommunicationException("connect refused")))
                .isInstanceOf(MeiliIndexAccessException.class)
                .hasMessageContaining("connect refused");
    }

    @Test
    @DisplayName("非 SDK 异常原样透传（不吞编程错误）")
    void otherThrowablesPassThroughUnchanged() {
        var boom = new IllegalStateException("bug");
        assertThat(MeiliErrors.translate(boom)).isSameAs(boom);
    }
}
