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
                    "MeiliSearch API error: " + api.getMessage(), api);
        }
        if (failure instanceof MeilisearchException sdk) {
            return new MeiliIndexAccessException(null,
                    "MeiliSearch access failure: " + sdk.getMessage(), sdk);
        }
        if (failure instanceof RuntimeException re) {
            return re;
        }
        return new MeiliIndexAccessException(null,
                "MeiliSearch access failure: " + failure.getMessage(), failure);
    }
}
