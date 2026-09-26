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
package io.github.lamspace.meili.repository.spike;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spike prototype: hand-rolled derived-query method-name parser (the replacement shape after the
 * structural-bridge route was disproven by commons 4.0's {@code Part.getProperty()} return-type
 * generation migration). The syntax (verb/By/top/OrderBy/And/Or/Not/keyword suffixes) is
 * hand-rolled; property segments are cut by longest-prefix match against a reflection dictionary
 * of the entity (abbreviations unsupported); semantics still land on core projection names via
 * {@link SpikePropertyResolver}.
 */
final class SpikeNameParser {

    /** Keyword → internal canonical name; stripping attempted in descending length order. */
    private static final Map<String, String> SUFFIX_KEYWORDS = new HashMap<>();
    /** Unsupported-surface keywords (rejected outright in v1). */
    private static final List<String> UNSUPPORTED =
            List.of("StartingWith", "EndingWith", "RegularExpression", "IsNotNull", "IsNull",
                    "IsNotEmpty", "IsEmpty", "Exists", "IgnoreCase");

    static {
        SUFFIX_KEYWORDS.put("Equals", "EQ");
        SUFFIX_KEYWORDS.put("Is", "EQ");
        SUFFIX_KEYWORDS.put("Between", "BETWEEN");
        SUFFIX_KEYWORDS.put("In", "IN");
        SUFFIX_KEYWORDS.put("LessThanEqual", "LTE");
        SUFFIX_KEYWORDS.put("GreaterThanEqual", "GTE");
        SUFFIX_KEYWORDS.put("LessThan", "LT");
        SUFFIX_KEYWORDS.put("GreaterThan", "GT");
        SUFFIX_KEYWORDS.put("Before", "LT");
        SUFFIX_KEYWORDS.put("After", "GT");
        SUFFIX_KEYWORDS.put("True", "TRUE");
        SUFFIX_KEYWORDS.put("False", "FALSE");
        SUFFIX_KEYWORDS.put("Containing", "CONTAINING");
        SUFFIX_KEYWORDS.put("Like", "LIKE");
        SUFFIX_KEYWORDS.put("Not", "NE");
    }

    private static final Pattern METHOD = Pattern.compile(
            "^[a-zA-Z]+?(?:Top(\\d+)|First(\\d+))?(Distinct)?By(.*?)(?:OrderBy(.+))?$");

    private SpikeNameParser() {
    }

    /** A single criteria clause. */
    record Clause(boolean or, boolean negate, String keyword, List<String> chain) {
    }

    /** A single order clause. */
    record Order(List<String> chain, boolean asc) {
    }

    /** The parse result. */
    record Parsed(Integer maxResults, boolean distinct, List<Clause> clauses, List<Order> orders) {
    }

    /**
     * Parses a derived-query method name.
     *
     * @param domainType the entity class (source of the property dictionary)
     * @param methodName the repository-interface method name
     * @return the structured parse result
     * @throws IllegalArgumentException invalid syntax or an unresolvable property segment
     * @throws UnsupportedOperationException a keyword unsupported in v1
     */
    static Parsed parse(Class<?> domainType, String methodName) {
        Matcher m = METHOD.matcher(methodName);
        if (!m.matches() || m.group(4) == null) {
            throw new IllegalArgumentException("cannot parse method name: " + methodName);
        }
        Integer top = null;
        if (m.group(1) != null) {
            top = Integer.valueOf(m.group(1));
        } else if (m.group(2) != null) {
            top = Integer.valueOf(m.group(2));
        }
        boolean distinct = m.group(3) != null;
        List<Clause> clauses = parseCriteria(domainType, m.group(4));
        List<Order> orders = m.group(5) == null ? List.of() : parseOrders(domainType, m.group(5));
        return new Parsed(top, distinct, clauses, orders);
    }

    private static List<Clause> parseCriteria(Class<?> type, String criteria) {
        List<Clause> out = new ArrayList<>();
        boolean or = false;
        for (String seg : splitDelimited(criteria)) {
            boolean negate = false;
            String s = seg;
            if (s.startsWith("Not") && s.length() > 3 && Character.isUpperCase(s.charAt(3))) {
                negate = true;
                s = s.substring(3);
            }
            String keyword = "EQ";
            for (String kw : SUFFIX_KEYWORDS.keySet().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
                if (s.endsWith(kw) && s.length() > kw.length()) {
                    keyword = SUFFIX_KEYWORDS.get(kw);
                    s = s.substring(0, s.length() - kw.length());
                    break;
                }
            }
            rejectUnsupported(seg);
            out.add(new Clause(or, negate, keyword, segment(type, s)));
            or = false;
            if (isOrSeparated(criteria, seg)) {
                or = true;
            }
        }
        return out;
    }

    /** Splits on upper-case And/Or delimiters, preserving occurrence order. */
    private static List<String> splitDelimited(String criteria) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < criteria.length() - 2; i++) {
            char c = criteria.charAt(i);
            if (c == 'A' || c == 'O') {
                String token = criteria.regionMatches(i, "And", 0, 3) ? "And"
                        : criteria.regionMatches(i, "Or", 0, 2) ? "Or" : null;
                if (token != null && i + token.length() < criteria.length()
                        && Character.isUpperCase(criteria.charAt(i + token.length()))
                        && i > start) {
                    parts.add(criteria.substring(start, i));
                    start = i + token.length();
                    i += token.length();
                }
            }
        }
        parts.add(criteria.substring(start));
        return parts;
    }

    /** The conjunction following a clause (and by default; or affects the next clause). */
    private static boolean isOrSeparated(String criteria, String seg) {
        int idx = criteria.indexOf(seg) + seg.length();
        return idx < criteria.length() && criteria.regionMatches(idx, "Or", 0, 2);
    }

    private static List<Order> parseOrders(Class<?> type, String orderPart) {
        List<Order> out = new ArrayList<>();
        for (String seg : splitDelimited(orderPart)) {
            boolean asc = true;
            String s = seg;
            if (s.endsWith("Desc")) {
                asc = false;
                s = s.substring(0, s.length() - 4);
            } else if (s.endsWith("Asc")) {
                s = s.substring(0, s.length() - 3);
            }
            out.add(new Order(segment(type, s), asc));
        }
        return out;
    }

    private static void rejectUnsupported(String seg) {
        for (String kw : UNSUPPORTED) {
            if (seg.endsWith(kw) && seg.length() > kw.length()) {
                throw new UnsupportedOperationException("unsupported keyword in v1: " + kw + " (method-name fragment " + seg + ")");
            }
        }
    }

    /** Longest-prefix split against the property dictionary: AuthorCity → [author, city]; abbreviations unsupported. */
    private static List<String> segment(Class<?> type, String path) {
        List<String> chain = new ArrayList<>();
        Class<?> current = type;
        int pos = 0;
        while (pos < path.length()) {
            String rest = path.substring(pos);
            Field match = null;
            for (Field f : candidates(current)) {
                if (rest.regionMatches(true, 0, f.getName(), 0, f.getName().length())
                        && (match == null || f.getName().length() > match.getName().length())) {
                    match = f;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException("cannot resolve property segment (abbreviations unsupported): " + path
                        + " @ " + current.getSimpleName());
            }
            chain.add(match.getName());
            pos += match.getName().length();
            current = match.getType();
        }
        return chain;
    }

    /** The current type's queryable field set (excluded fields included, so the error can be precise). */
    private static List<Field> candidates(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
