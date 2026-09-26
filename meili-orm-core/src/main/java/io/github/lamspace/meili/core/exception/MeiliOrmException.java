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
 * Unchecked root of the whole meili-orm core exception hierarchy.
 *
 * <p>Every failure surfaced by core — mapping validation, document serialization,
 * server access, task waiting — is normalized into a subclass of this exception so
 * that callers can intercept the entire library with a single {@code catch} and never
 * depend on transport-layer exception types.
 *
 * <p>Instances are thrown, never caught-and-stored; they carry no mutable state and
 * are therefore trivially thread-safe.
 */
public class MeiliOrmException extends RuntimeException {

    /** Fixed serialization version identifier. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a root exception with a detail message.
     *
     * @param message human-readable failure description
     */
    public MeiliOrmException(String message) {
        super(message);
    }

    /**
     * Creates a root exception wrapping a lower-level cause.
     *
     * @param message human-readable failure description
     * @param cause the underlying failure being translated
     */
    public MeiliOrmException(String message, Throwable cause) {
        super(message, cause);
    }
}
