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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Spring Data style derived-query method names.
 *
 * <p><b>Why not commons' {@code PartTree}.</b> The commons property-model surface needed to
 * read a parsed part (its property path) changes signature between the two commons
 * generations this project supports, which would break single-jar compatibility
 * (binary-diff evidence: change spike record). This parser therefore owns the grammar;
 * commons' stable {@code org.springframework.data.domain} types remain usable everywhere
 * else. Keyword semantics map 1:1 onto the supported subset defined by the
 * {@code repository-derived-queries} capability.
 *
 * <p><b>Grammar.</b> {@code <verb>[Top<N>|First<N>][Distinct]By<criteria>[OrderBy<orders>]}
 * with {@code verb ∈ {find, read, get, retrieve}}. Criteria chains split at uppercase
 * {@code And}/{@code Or} delimiters (And binds tighter than Or). A condition is
 * {@code [Not]<property>[<keyword>]} with trailing keywords resolved longest-first; {@code True}/
 * {@code False} take no argument, {@code Between} takes two. Property segments are resolved
 * against the entity's declared field dictionary by longest-prefix matching — abbreviations
 * are rejected, not guessed.
 *
 * <p><b>Failure mode.</b> Malformed grammar, unknown segments and unsupported keywords throw
 * at parse time with the method name embedded; callers run this at bootstrap.
 */
public final class MeiliMethodNames {

    /** Recognized query verbs; anything else (count/exists/delete projections) is rejected. */
    private static final List<String> VERBS = List.of("find", "read", "get", "retrieve");

    /** Suffix keyword table, longest-first matching. */
    private static final Map<String, Keyword> SUFFIXES = new HashMap<>();
    /** Explicitly unsupported keyword shapes (present in Spring Data grammar, out of scope). */
    private static final List<String> UNSUPPORTED = List.of(
            "StartingWith", "EndingWith", "RegularExpression", "IsNotNull", "IsNull",
            "IsNotEmpty", "IsEmpty", "Exists", "IgnoreCase");

    static {
        SUFFIXES.put("GreaterThanEqual", Keyword.GTE);
        SUFFIXES.put("LessThanEqual", Keyword.LTE);
        SUFFIXES.put("GreaterThan", Keyword.GT);
        SUFFIXES.put("LessThan", Keyword.LT);
        SUFFIXES.put("Between", Keyword.BETWEEN);
        SUFFIXES.put("Containing", Keyword.CONTAINING);
        SUFFIXES.put("Not", Keyword.NE);
        SUFFIXES.put("Before", Keyword.LT);
        SUFFIXES.put("After", Keyword.GT);
        SUFFIXES.put("Equals", Keyword.EQ);
        SUFFIXES.put("True", Keyword.TRUE);
        SUFFIXES.put("False", Keyword.FALSE);
        SUFFIXES.put("Like", Keyword.LIKE);
        SUFFIXES.put("In", Keyword.IN);
        SUFFIXES.put("Is", Keyword.EQ);
    }

    /** Grammar: verb [TopN|FirstN] [Distinct] By criteria [OrderBy terms]. */
    private static final Pattern METHOD = Pattern.compile(
            "^([a-zA-Z]+?)(?:Top(\\d*)|First(\\d*))?(Distinct)?By(.*?)(?:OrderBy(.+))?$");

    /** Comparison/search keywords after grammar resolution. */
    public enum Keyword {
        /** Equality. */
        EQ,
        /** Inequality. */
        NE,
        /** Strict greater-than. */
        GT,
        /** Greater-or-equal. */
        GTE,
        /** Strict less-than. */
        LT,
        /** Less-or-equal. */
        LTE,
        /** Inclusive [a, b] range. */
        BETWEEN,
        /** Membership list. */
        IN,
        /** Boolean true literal. */
        TRUE,
        /** Boolean false literal. */
        FALSE,
        /** Full-text contains (maps to q + attributesToSearchOn). */
        CONTAINING,
        /** Alias of CONTAINING in v1 (wildcards ignored). */
        LIKE
    }

    /**
     * One atomic condition.
     *
     * @param negate  whether the condition is negated
     * @param keyword resolved comparison/search keyword
     * @param chain   raw camel property string pending dictionary segmentation
     */
    public record Clause(boolean negate, Keyword keyword, List<String> chain) {
    }

    /**
     * An AND-conjunction of clauses; groups between them are OR-ed.
     *
     * @param clauses conjunction members in order
     */
    public record Group(List<Clause> clauses) {
    }

    /**
     * One ORDER BY term.
     *
     * @param chain raw camel property string pending dictionary segmentation
     * @param asc   ascending direction flag
     */
    public record Order(List<String> chain, boolean asc) {
    }

    /**
     * Full parse result of one method name.
     *
     * @param maxResults TopN row cap or {@code null}
     * @param distinct   Distinct modifier presence (rejected downstream)
     * @param groups     OR-groups of AND-conjunctions
     * @param orders     ORDER BY terms in declaration order
     * @param methodName original method name for diagnostics
     */
    public record Parsed(Integer maxResults, boolean distinct, List<Group> groups,
                         List<Order> orders, String methodName) {

        /**
         * Total number of bound value arguments the grammar expects (excluding
         * Pageable/Sort parameters, which callers filter separately).
         *
         * @return positional argument count for condition values
         */
        public int expectedValueArguments() {
            int n = 0;
            for (Group g : groups) {
                for (Clause c : g.clauses()) {
                    n += switch (c.keyword()) {
                        case BETWEEN -> 2;
                        case TRUE, FALSE -> 0;
                        default -> 1;
                    };
                }
            }
            return n;
        }
    }

    /** Utility — no instances. */
    private MeiliMethodNames() {
    }

    /**
     * Parses a derived-query method name.
     *
     * @param methodName the repository interface method name
     * @return the structured parse result
     * @throws IllegalArgumentException for malformed grammar or an unsupported verb/keyword;
     *                                  the message always contains the method name
     */
    public static Parsed parse(String methodName) {
        Matcher m = METHOD.matcher(methodName);
        if (!m.matches() || m.group(5) == null || m.group(5).isEmpty()) {
            throw new IllegalArgumentException("Method name does not match the derived-query grammar <verb>[TopN]By…[OrderBy…] (count/exists/delete derived queries are not supported): "
                    + methodName);
        }
        String verb = m.group(1).toLowerCase();
        if (VERBS.stream().noneMatch(verb::startsWith)) {
            throw new IllegalArgumentException("v1 supports only derived queries starting with find/read/get/retrieve: " + methodName);
        }
        Integer top = null;
        if (m.group(2) != null) {
            top = m.group(2).isEmpty() ? 1 : Integer.valueOf(m.group(2));
        } else if (m.group(3) != null) {
            top = m.group(3).isEmpty() ? 1 : Integer.valueOf(m.group(3));
        }
        boolean distinct = m.group(4) != null;
        List<Group> groups = parseCriteria(m.group(5), methodName);
        List<Order> orders = m.group(6) == null ? List.of() : parseOrders(m.group(6), methodName);
        return new Parsed(top, distinct, groups, orders, methodName);
    }

    /**
     * Criteria → OR-groups of AND-clauses.
     *
     * @param criteria   raw criteria text
     * @param methodName method name for error location
     * @return non-empty group list
     */
    private static List<Group> parseCriteria(String criteria, String methodName) {
        List<String[]> tokens = splitTopLevel(criteria); // {text, delimiterFollowing}
        List<Clause> current = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        for (String[] token : tokens) {
            rejectUnsupported(token[0], methodName);
            current.add(parseClause(token[0], methodName));
            if ("Or".equals(token[1])) {
                groups.add(new Group(List.copyOf(current)));
                current = new ArrayList<>();
            }
        }
        groups.add(new Group(List.copyOf(current)));
        return groups;
    }

    /**
     * Split at top-level uppercase {@code And}/{@code Or} delimiters. Each entry carries the
     * chunk text plus the delimiter that follows it ({@code "And"}/{@code "Or"}/empty for last).
     *
     * @param criteria raw text to split
     * @return token pairs in order
     */
    private static List<String[]> splitTopLevel(String criteria) {
        List<String[]> out = new ArrayList<>();
        int start = 0;
        int i = 1;
        while (i < criteria.length()) {
            char c = criteria.charAt(i);
            String token = (c == 'A' && criteria.startsWith("And", i)) ? "And"
                    : (c == 'O' && criteria.startsWith("Or", i)) ? "Or" : null;
            if (token != null && i > start && i + token.length() < criteria.length()
                    && Character.isUpperCase(criteria.charAt(i + token.length()))) {
                out.add(new String[]{criteria.substring(start, i), token});
                start = i + token.length();
                i = start;
            } else {
                i++;
            }
        }
        out.add(new String[]{criteria.substring(start), ""});
        return out;
    }

    /**
     * Turns one criteria segment into a clause (Not-prefix + keyword-suffix resolution).
     *
     * @param segment    raw criteria segment
     * @param methodName method name for error location
     * @return resolved clause
     */
    private static Clause parseClause(String segment, String methodName) {
        boolean negate = false;
        String s = segment;
        if (s.startsWith("Not") && s.length() > 3 && Character.isUpperCase(s.charAt(3))) {
            negate = true;
            s = s.substring(3);
        }
        Keyword keyword = Keyword.EQ;
        for (String suffix : SUFFIXES.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            if (s.endsWith(suffix) && s.length() > suffix.length()) {
                keyword = SUFFIXES.get(suffix);
                s = s.substring(0, s.length() - suffix.length());
                break;
            }
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Clause is missing its property segment: " + methodName + " segment " + segment);
        }
        return new Clause(negate, keyword, List.of(s));
    }

    /**
     * Splits OrderBy terms; property/chain segmentation happens later via the dictionary.
     *
     * @param orderPart  raw OrderBy text
     * @param methodName method name for error location
     * @return order terms in declaration order
     */
    private static List<Order> parseOrders(String orderPart, String methodName) {
        List<Order> out = new ArrayList<>();
        for (String[] pair : splitTopLevel(orderPart)) {
            String seg = pair[0];
            boolean asc = true;
            String s = seg;
            if (s.endsWith("Desc")) {
                asc = false;
                s = s.substring(0, s.length() - 4);
            } else if (s.endsWith("Asc")) {
                s = s.substring(0, s.length() - 3);
            }
            if (s.isEmpty()) {
                throw new IllegalArgumentException("OrderBy clause is missing a property: " + methodName);
            }
            out.add(new Order(List.of(s), asc));
        }
        return out;
    }

    /**
     * Rejects keywords present in Spring Data grammar but out of v1 scope.
     *
     * @param segment    raw criteria segment
     * @param methodName method name for error location
     */
    private static void rejectUnsupported(String segment, String methodName) {
        for (String kw : UNSUPPORTED) {
            if (segment.endsWith(kw) && segment.length() > kw.length()) {
                throw new IllegalArgumentException("v1 does not support keyword " + kw + " (method " + methodName
                        + " segment " + segment + "); see the derived-query support table in the mapping guide");
            }
        }
    }
}
