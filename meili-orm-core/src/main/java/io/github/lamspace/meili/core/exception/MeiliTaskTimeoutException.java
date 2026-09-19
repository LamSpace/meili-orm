package io.github.lamspace.meili.core.exception;

/**
 * Raised when a task does not reach a terminal state within the allotted wait budget
 * ({@code awaitTask} or a write performed under the wait-task option).
 *
 * <p>The task itself keeps running server-side; timing out only abandons the wait, so
 * callers may retry observation via the task query operations. The message always
 * embeds the task uid and the timeout amount.
 *
 * <p>Immutable and thread-safe.
 */
public final class MeiliTaskTimeoutException extends MeiliOrmException {

    /** Fixed serialization version identifier. */
    private static final long serialVersionUID = 1L;

    /** Uid of the task whose wait timed out. */
    private final int taskUid;

    /** Wait budget that expired, in milliseconds. */
    private final long timeoutMs;

    /**
     * Creates a task-wait timeout.
     *
     * @param taskUid   uid of the task that did not finish in time
     * @param timeoutMs the wait budget that expired, in milliseconds
     */
    public MeiliTaskTimeoutException(int taskUid, long timeoutMs) {
        super("等待任务超时: uid=" + taskUid + ", timeoutMs=" + timeoutMs);
        this.taskUid = taskUid;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns the uid of the timed-out task.
     *
     * @return the task uid
     */
    public int getTaskUid() {
        return taskUid;
    }

    /**
     * Returns the wait budget that expired.
     *
     * @return timeout in milliseconds
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }
}
