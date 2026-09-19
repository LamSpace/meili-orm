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

/** {@link SdkMeiliRawGateway#awaitTask} 与 {@code getTask} 的确定性行为测试（client mock）。 */
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
    @DisplayName("未到终态且预算耗尽 → MeiliTaskTimeoutException（含 uid 与预算）")
    void timeoutWhenNotTerminal() {
        Task pending = sdkTask(TaskStatus.PROCESSING, null);
        when(client.getTask(anyInt())).thenReturn(pending);
        assertThatThrownBy(() -> gateway.awaitTask(1, Duration.ofMillis(80)))
                .isInstanceOf(MeiliTaskTimeoutException.class)
                .hasMessageContaining("uid=1");
    }

    @Test
    @DisplayName("SUCCEEDED 终态 → 正常返回")
    void returnsOnSucceeded() {
        Task done = sdkTask(TaskStatus.SUCCEEDED, null);
        when(client.getTask(anyInt())).thenReturn(done);
        gateway.awaitTask(1, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("FAILED 终态 → IndexAccessException 携任务错误详情")
    void failedTaskThrowsWithDetail() {
        Task failed = sdkTask(TaskStatus.FAILED, "primary key cannot be changed");
        when(client.getTask(anyInt())).thenReturn(failed);
        assertThatThrownBy(() -> gateway.awaitTask(1, Duration.ofSeconds(5)))
                .isInstanceOf(MeiliIndexAccessException.class)
                .hasMessageContaining("primary key cannot be changed");
    }

    @Test
    @DisplayName("getTask 映射为不可变 core 视图（枚举按常量比较）")
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
    @DisplayName("网关入口异常统一翻译，不抛穿 SDK 异常")
    void clientErrorsTranslated() {
        when(client.getTask(anyInt())).thenThrow(new MeilisearchApiException(
                new APIError().setCode("task_not_found").setMessage("no task")));
        assertThatThrownBy(() -> gateway.getTask(9))
                .isInstanceOf(MeiliIndexAccessException.class)
                .satisfies(t -> assertThat(((MeiliIndexAccessException) t).getMeiliCode())
                        .isEqualTo("task_not_found"));
    }
}
