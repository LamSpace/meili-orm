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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.exceptions.APIError;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskError;
import com.meilisearch.sdk.model.TaskStatus;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.core.exception.MeiliTaskTimeoutException;
import io.github.lamspace.meili.core.task.MeiliTask;
import io.github.lamspace.meili.core.task.MeiliTaskStatus;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Deterministic behavior tests for {@link SdkMeiliRawGateway#awaitTask} and {@code getTask} (mocked client). */
class SdkMeiliRawGatewayAwaitTest {

    Client client;
    SdkMeiliRawGateway gateway;

    @BeforeEach
    void up() {
        client = mock(Client.class);
        gateway = new SdkMeiliRawGateway(client, new Config("http://localhost:1", "k"));
    }

    private static Task sdkTask(TaskStatus status, String error) {
        Task task = mock(Task.class);
        when(task.getUid()).thenReturn(1);
        when(task.getStatus()).thenReturn(status);
        when(task.getType()).thenReturn("indexCreation");
        when(task.getIndexUid()).thenReturn("idx");
        if (error != null) {
            TaskError te = mock(TaskError.class);
            when(te.getMessage()).thenReturn(error);
            when(task.getError()).thenReturn(te);
        }
        return task;
    }

    @Test
    @DisplayName("Non-terminal when the budget runs out → MeiliTaskTimeoutException (carries uid and budget)")
    void timeoutWhenNotTerminal() {
        Task pending = sdkTask(TaskStatus.PROCESSING, null);
        when(client.getTask(anyInt())).thenReturn(pending);
        assertThatThrownBy(() -> gateway.awaitTask(1, Duration.ofMillis(80)))
                .isInstanceOf(MeiliTaskTimeoutException.class)
                .hasMessageContaining("uid=1");
    }

    @Test
    @DisplayName("SUCCEEDED terminal state → returns normally")
    void returnsOnSucceeded() {
        Task done = sdkTask(TaskStatus.SUCCEEDED, null);
        when(client.getTask(anyInt())).thenReturn(done);
        gateway.awaitTask(1, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("FAILED terminal state → IndexAccessException carrying the task error detail")
    void failedTaskThrowsWithDetail() {
        Task failed = sdkTask(TaskStatus.FAILED, "primary key cannot be changed");
        when(client.getTask(anyInt())).thenReturn(failed);
        assertThatThrownBy(() -> gateway.awaitTask(1, Duration.ofSeconds(5)))
                .isInstanceOf(MeiliIndexAccessException.class)
                .hasMessageContaining("primary key cannot be changed");
    }

    @Test
    @DisplayName("getTask maps to an immutable core view (enums compared by constant)")
    void getTaskMapsToCoreView() {
        Task enqueued = sdkTask(TaskStatus.ENQUEUED, null);
        when(client.getTask(anyInt())).thenReturn(enqueued);
        MeiliTask task = gateway.getTask(1);
        assertThat(task.uid()).isEqualTo(1);
        assertThat(task.status()).isEqualTo(MeiliTaskStatus.ENQUEUED);
        assertThat(task.type()).isEqualTo("indexCreation");
        assertThat(task.indexUid()).isEqualTo("idx");
        assertThat(task.errorMessage()).isNull();
        assertThat(task.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Gateway entry exceptions are translated uniformly; SDK exceptions never leak through")
    void clientErrorsTranslated() {
        when(client.getTask(anyInt())).thenThrow(new MeilisearchApiException(
                new APIError().setCode("task_not_found").setMessage("no task")));
        assertThatThrownBy(() -> gateway.getTask(9))
                .isInstanceOf(MeiliIndexAccessException.class)
                .satisfies(t -> assertThat(((MeiliIndexAccessException) t).getMeiliCode())
                        .isEqualTo("task_not_found"));
    }
}
