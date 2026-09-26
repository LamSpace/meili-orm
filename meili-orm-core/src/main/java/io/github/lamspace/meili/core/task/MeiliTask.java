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
package io.github.lamspace.meili.core.task;

/**
 * Immutable core-owned snapshot of one server task — the transport model type never
 * escapes the internal gateway. {@code indexUid} is the index the task targets (may be
 * absent for instance-level tasks), and {@code errorMessage} is only non-null once the
 * task reached {@link MeiliTaskStatus#FAILED}.
 *
 * @param uid          server task uid
 * @param status       current lifecycle status
 * @param type         server task type string (e.g. {@code documentAdditionOrUpdate}),
 *                     kept verbatim
 * @param indexUid     target index uid, or {@code null}
 * @param errorMessage failure detail when the task failed, else {@code null}
 */
public record MeiliTask(int uid, MeiliTaskStatus status, String type, String indexUid,
                        String errorMessage) {

    /**
     * Reports whether the task will not change status any more.
     *
     * @return {@code true} for SUCCEEDED / FAILED / CANCELED
     */
    public boolean isTerminal() {
        return status == MeiliTaskStatus.SUCCEEDED || status == MeiliTaskStatus.FAILED
                || status == MeiliTaskStatus.CANCELED;
    }
}
