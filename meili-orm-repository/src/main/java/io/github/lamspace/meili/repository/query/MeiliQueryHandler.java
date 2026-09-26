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
package io.github.lamspace.meili.repository.query;

/**
 * A fully-resolved repository query method: all grammar parsing, property bridging,
 * role pre-checks and shape validation happened at bootstrap; invocation only binds
 * runtime arguments and executes.
 */
@FunctionalInterface
public interface MeiliQueryHandler {

    /**
     * Runs the resolved query against runtime arguments.
     *
     * @param args method arguments in declaration order (never {@code null}, may be empty)
     * @return the adapted return value ({@code List}/{@code Optional}/{@code Page})
     * @throws Throwable application failures propagate from the operations layer
     */
    Object invoke(Object[] args) throws Throwable;
}
