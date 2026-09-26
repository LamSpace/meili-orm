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
package io.github.lamspace.meili.core.exception;

/**
 * Raised when entity metadata violates the mapping contract: a missing or duplicated
 * primary-key declaration, an unsupported primary-key type, a projection-name conflict,
 * an illegal role annotation on a container field, an audit field outside its allowed
 * type set, or a broken settings passthrough.
 *
 * <p>This exception is a <em>startup fail-fast</em> signal: it is thrown while the
 * metamodel or the settings projection is being built, before any server request is
 * issued, and it always names the offending entity and member in its message so the
 * declaration can be fixed without further diagnosis.
 *
 * <p>Immutable and thread-safe.
 */
public final class MeiliMappingException extends MeiliOrmException {

    /** Fixed serialization version identifier. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a mapping failure with a detail message.
     *
     * @param message description naming the offending entity/member or resource
     */
    public MeiliMappingException(String message) {
        super(message);
    }

    /**
     * Creates a mapping failure wrapping an underlying cause (e.g. an IO error while
     * reading a passthrough settings resource).
     *
     * @param message description naming the offending entity/member or resource
     * @param cause the underlying failure being translated
     */
    public MeiliMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
