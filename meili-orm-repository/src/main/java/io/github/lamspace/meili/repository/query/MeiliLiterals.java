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
            throw new IllegalArgumentException("查询条件不接受 null 参数（派生查询不支持可选条件；如需缺省匹配请显式分支）");
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
        throw new IllegalArgumentException("不支持的查询参数类型: " + value.getClass().getName());
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
        throw new IllegalArgumentException("不支持的时间参数类型: " + value.getClass().getName());
    }

    /**
     * Rejects non-finite doubles which have no filter DSL spelling.
     *
     * @param d value to check
     */
    private static void requireFinite(double d) {
        if (!Double.isFinite(d)) {
            throw new IllegalArgumentException("浮点条件参数必须是有限值: " + d);
        }
    }
}
