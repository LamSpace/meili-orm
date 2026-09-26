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
package io.github.lamspace.meili.repository.exception;

/**
 * Repository bootstrap configuration error: a method's grammar, argument shape or return
 * type violates the supported surface. Extends {@link IllegalArgumentException} so callers
 * can treat every startup-time repository failure uniformly (the derived-query capability
 * contract specifies this exception family for unsupported shapes; mapping-level role
 * failures use the core {@code MeiliMappingException}).
 */
public class MeiliRepositoryConfigurationException extends IllegalArgumentException {

    /**
     * Creates the exception with a locating message.
     *
     * @param message locating detail (always includes the offending method name)
     */
    public MeiliRepositoryConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message locating detail
     * @param cause   underlying failure
     */
    public MeiliRepositoryConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
