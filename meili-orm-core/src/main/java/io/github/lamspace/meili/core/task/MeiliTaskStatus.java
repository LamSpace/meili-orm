package io.github.lamspace.meili.core.task;

/**
 * Lifecycle status of an asynchronous MeiliSearch task. The terminal states are
 * {@link #SUCCEEDED}, {@link #FAILED} and {@link #CANCELED}; anything before that may
 * still change.
 */
public enum MeiliTaskStatus {

    /** Accepted by the server, not started yet. */
    ENQUEUED,

    /** Currently being processed. */
    PROCESSING,

    /** Terminal: completed successfully. */
    SUCCEEDED,

    /** Terminal: aborted with an error (see the task's error detail). */
    FAILED,

    /** Terminal: canceled before or during processing. */
    CANCELED,
}
