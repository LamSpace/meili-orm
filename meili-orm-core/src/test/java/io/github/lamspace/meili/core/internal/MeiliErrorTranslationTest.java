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

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.exceptions.APIError;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.exceptions.MeilisearchTimeoutException;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link MeiliErrors#translate}, the unified exception exit. */
class MeiliErrorTranslationTest {

    @Test
    @DisplayName("API exception → MeiliIndexAccessException with the server error code readable")
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
    @DisplayName("Communication/timeout SDK exceptions → access exception without a code (task-wait timeout takes another path)")
    void transportFailuresBecomeIndexAccessWithoutCode() {
        assertThat(MeiliErrors.translate(new MeilisearchTimeoutException("read timeout")))
                .isInstanceOf(MeiliIndexAccessException.class);
        assertThat(MeiliErrors.translate(new MeilisearchCommunicationException("connect refused")))
                .isInstanceOf(MeiliIndexAccessException.class)
                .hasMessageContaining("connect refused");
    }

    @Test
    @DisplayName("Non-SDK throwables pass through unchanged (programming errors are not swallowed)")
    void otherThrowablesPassThroughUnchanged() {
        var boom = new IllegalStateException("bug");
        assertThat(MeiliErrors.translate(boom)).isSameAs(boom);
    }
}
