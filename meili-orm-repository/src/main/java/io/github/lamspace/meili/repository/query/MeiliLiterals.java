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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.Temporal;

/**
 * Renders Java values as MeiliSearch filter-DSL literals. This is the single escaping point
 * shared by derived and annotated queries — template authors never write quotes around
 * substituted string values, and no caller path can inject raw DSL through a parameter.
 *
 * <p><b>Rules.</b> Strings, characters and enums render as double-quoted literals with
 * backslash and double-quote escaped; numbers and booleans render bare (NaN/infinite floats
 * are rejected as they have no DSL spelling); the common temporal types render as bare
 * ISO-8601/RFC-3339 strings per the server's date-filter syntax. Anything else — including
 * {@code null} — throws {@link IllegalArgumentException} with a locating message, because a
 * silently "skipped" condition would change query meaning.
 */
public final class MeiliLiterals {

    /** Utility — no instances. */
    private MeiliLiterals() {
    }

    /**
     * Renders one scalar as a filter literal.
     *
     * @param value non-null parameter value
     * @return DSL text
     * @throws IllegalArgumentException for null, unsupported types or non-finite numbers
     */
    public static String of(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Query conditions do not accept null arguments (derived queries have no optional-condition support; branch explicitly if a default match is needed)");
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Double d) {
            requireFinite(d);
            return d.toString();
        }
        if (value instanceof Float f) {
            requireFinite(f.doubleValue());
            return f.toString();
        }
        if (value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger) {
            return value.toString();
        }
        if (value instanceof String s) {
            return quoted(s);
        }
        if (value instanceof Character c) {
            return quoted(c.toString());
        }
        if (value instanceof Enum<?> e) {
            return quoted(e.name());
        }
        if (value instanceof Temporal) {
            return temporal(value);
        }
        throw new IllegalArgumentException("Unsupported query parameter type: " + value.getClass().getName());
    }

    /**
     * Wraps text as an escaped double-quoted Meili string literal.
     *
     * @param raw unescaped text
     * @return quoted literal safe for the filter DSL
     */
    public static String quoted(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    /**
     * Renders a supported temporal as a bare ISO-8601 literal.
     *
     * @param value temporal value
     * @return ISO text
     */
    private static String temporal(Object value) {
        if (value instanceof LocalDate || value instanceof LocalDateTime
                || value instanceof OffsetDateTime || value instanceof Instant) {
            return value.toString();
        }
        throw new IllegalArgumentException("Unsupported temporal parameter type: " + value.getClass().getName());
    }

    /**
     * Rejects non-finite doubles which have no filter DSL spelling.
     *
     * @param d value to check
     */
    private static void requireFinite(double d) {
        if (!Double.isFinite(d)) {
            throw new IllegalArgumentException("Floating-point condition argument must be finite: " + d);
        }
    }
}
