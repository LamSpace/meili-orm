package io.github.lamspace.meili.core.internal;

import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;

/**
 * The single funnel from transport-layer failures to the public exception hierarchy:
 * API-level failures keep their server error code, communication/timeout failures arrive
 * without one, and anything that is not a client exception (programming errors alike)
 * passes through untouched.
 *
 * <p>Both gateway channels — official client calls and the HTTP helper — translate through
 * this class so callers observe one error vocabulary regardless of path. Pure function;
 * thread-safe.
 */
public final class MeiliErrors {

    /**
     * Utility holder.
     */
    private MeiliErrors() {
    }

    /**
     * Translates one caught throwable into a public core exception.
     *
     * @param failure the caught throwable
     * @return a runtime exception safe to propagate from operations — the input itself
     *         when it is neither a client exception nor a checked failure
     */
    public static RuntimeException translate(Throwable failure) {
        if (failure instanceof MeilisearchApiException api) {
            return new MeiliIndexAccessException(api.getCode(),
                    "MeiliSearch API 错误: " + api.getMessage(), api);
        }
        if (failure instanceof MeilisearchException sdk) {
            return new MeiliIndexAccessException(null,
                    "MeiliSearch 访问失败: " + sdk.getMessage(), sdk);
        }
        if (failure instanceof RuntimeException re) {
            return re;
        }
        return new MeiliIndexAccessException(null,
                "MeiliSearch 访问失败: " + failure.getMessage(), failure);
    }
}
