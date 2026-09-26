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

import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.repository.exception.MeiliRepositoryConfigurationException;
import io.github.lamspace.meili.repository.support.MeiliPropertyPaths;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Bootstrap-time resolver for repository methods annotated with
 * {@link io.github.lamspace.meili.repository.MeiliQuery}.
 *
 * <p><b>Binding.</b> Placeholders {@code :name} (from {@code @Param} or compiled parameter
 * names) and {@code ?N} (positional, counting only value parameters — Pageable/Sort carriers
 * are never template variables) map to SpEL variables; raw {@code #{...}} expressions
 * evaluate against the same bindings (property access + toString). In filter templates every
 * substituted value renders through {@link MeiliLiterals} (Strings auto-quoted/escaped —
 * templates never carry the quotes), so parameter values cannot alter DSL structure; in q
 * templates values substitute verbatim as full text.
 *
 * <p><b>Bootstrap validation.</b> At least one of q/filter/distinct must be set; named and
 * positional placeholders must be bindable; filter parentheses must balance outside string
 * literals; {@code distinct} accepts no placeholders. Runtime SpEL failures wrap into
 * {@link MeiliOrmException} naming the expression and the method.
 *
 * <p><b>Precedence with the method name.</b> The annotation owns q/filter/distinct; a
 * trailing {@code OrderBy} (sortable-prechecked) and {@code TopN}/{@code FirstN} still come
 * from the name; remaining {@code By…} criteria are ignored with a startup WARN listing the
 * ignored segments.
 */
public final class MeiliAnnotatedQueries {

    /** Diagnostics for ignored name criteria. */
    private static final Logger log = LoggerFactory.getLogger(MeiliAnnotatedQueries.class);

    /** Parameter-name discovery consistent with the build's {@code -parameters} flag. */
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /** Shared SpEL parser (thread-safe). */
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /** Named placeholder grammar. */
    private static final Pattern NAMED = Pattern.compile(":(\\w+)");
    /** Positional placeholder grammar. */
    private static final Pattern POSITIONAL = Pattern.compile("\\?(\\d+)");
    /** TopN/FirstN extraction anchored at the By keyword. */
    private static final Pattern TOP = Pattern.compile("(?:Top(\\d*)|First(\\d*))By");
    /** Trailing OrderBy capture group. */
    private static final Pattern ORDER_SUFFIX = Pattern.compile("OrderBy(.+)$");
    /** Criteria extraction for the ignored-part warning. */
    private static final Pattern CRITERIA = Pattern.compile("[a-zA-Z]+?(?:Top\\d*|First\\d*)?Distinct?By(.*?)(?:OrderBy.+)?$");

    /** Utility — no instances. */
    private MeiliAnnotatedQueries() {
    }

    /**
     * Resolves one annotated method at bootstrap.
     *
     * @param method     repository method carrying {@code @MeiliQuery}
     * @param entity     bound domain metamodel
     * @param operations template for execution
     * @return immutable handler
     * @throws MeiliRepositoryConfigurationException for empty templates, unresolvable
     *                                               placeholders, unbalanced parentheses or
     *                                               unsupported return shapes
     */
    public static MeiliQueryHandler bootstrap(Method method, MeiliPersistentEntity entity,
                                              MeiliSearchOperations operations) {
        io.github.lamspace.meili.repository.MeiliQuery ann =
                method.getAnnotation(io.github.lamspace.meili.repository.MeiliQuery.class);
        String methodName = method.getName();
        if (ann.q().isEmpty() && ann.filter().isEmpty() && ann.distinct().isEmpty()) {
            throw new MeiliRepositoryConfigurationException(
                    "@MeiliQuery must provide at least one of q/filter/distinct: " + methodName);
        }
        checkBrackets(ann.filter(), methodName);
        if (ann.distinct().contains(":") || ann.distinct().contains("?") || ann.distinct().contains("#{")) {
            throw new MeiliRepositoryConfigurationException(
                    "The distinct attribute accepts no placeholders (must be a literal projection path): " + methodName);
        }

        Map<String, Integer> byName = new HashMap<>();
        List<Integer> valueIdx = new ArrayList<>();
        int pageableIndex = -1;
        String[] discovered = NAME_DISCOVERER.getParameterNames(method);
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Class<?> t = params[i].getType();
            if (Pageable.class.isAssignableFrom(t) || Sort.class.isAssignableFrom(t)) {
                pageableIndex = i;
                continue;
            }
            valueIdx.add(i);
            Param p = params[i].getAnnotation(Param.class);
            String name = p != null ? p.value() : (discovered != null ? discovered[i] : null);
            if (name != null && !name.isEmpty()) {
                byName.put(name, i);
            }
        }
        validatePlaceholders(ann.q(), byName, valueIdx.size(), methodName);
        validatePlaceholders(ann.filter(), byName, valueIdx.size(), methodName);

        String shape = shapeOf(method);

        // Order/top still from the name; remaining criteria ignored with WARN.
        Integer top = null;
        Matcher tm = TOP.matcher(methodName);
        if (tm.find()) {
            top = tm.group(1) == null || tm.group(1).isEmpty() ? 1
                    : Integer.valueOf(tm.group(1));
        }
        List<String[]> orders = new ArrayList<>();
        Matcher om = ORDER_SUFFIX.matcher(methodName);
        if (om.find()) {
            for (String seg : om.group(1).split("And(?=[A-Z])")) {
                boolean asc = !seg.endsWith("Desc");
                String raw = seg.endsWith("Desc") || seg.endsWith("Asc")
                        ? seg.substring(0, seg.length() - 3) : seg;
                List<String> segments = MeiliPropertyPaths.splitCamel(entity.getType(),
                        methodName, raw);
                String path = MeiliPropertyPaths.resolveChain(entity.getType(), methodName, segments);
                requireSortable(path, entity, methodName);
                orders.add(new String[]{path, asc ? "asc" : "desc"});
            }
        }
        Matcher cm = CRITERIA.matcher(methodName);
        if (cm.matches() && !cm.group(1).isEmpty()) {
            log.warn("Method {} is annotated with @MeiliQuery: name criteria segment {} is short-circuited and ignored by the annotation (order/top still apply)",
                    methodName, cm.group(1));
        }

        final Integer topFinal = top;
        final int pageableFinal = pageableIndex;
        final List<String> renderedOrders = orders.stream().map(o -> o[0] + ":" + o[1]).toList();
        return args -> execute(ann, byName, valueIdx, args, pageableFinal, topFinal,
                renderedOrders, shape, entity, methodName, operations);
    }

    /**
     * Renders templates, runs the search and adapts the return shape.
     *
     * @param ann          annotation values
     * @param byName       bindable parameter names to argument indices
     * @param valueIdx     value argument indices in positional order
     * @param args         runtime arguments
     * @param pageableIndex Pageable/Sort argument index or -1
     * @param top          TopN cap from the method name or {@code null}
     * @param nameOrders   pre-rendered OrderBy expressions from the method name
     * @param shape        return shape label
     * @param entity       bound metamodel
     * @param methodName   diagnostics
     * @param operations   execution template
     * @return adapted result
     */
    private static Object execute(io.github.lamspace.meili.repository.MeiliQuery ann,
                                  Map<String, Integer> byName, List<Integer> valueIdx, Object[] args,
                                  int pageableIndex, Integer top, List<String> nameOrders,
                                  String shape, MeiliPersistentEntity entity, String methodName,
                                  MeiliSearchOperations operations) {
        Map<String, Object> vars = new HashMap<>();
        byName.forEach((n, i) -> vars.put(n, args[i]));
        for (int slot = 0; slot < valueIdx.size(); slot++) {
            vars.put("arg" + slot, args[valueIdx.get(slot)]);
        }
        io.github.lamspace.meili.core.query.MeiliQuery mq =
                io.github.lamspace.meili.core.query.MeiliQuery.query(
                        ann.q().isEmpty() ? null : render(ann.q(), vars, false, methodName));
        if (!ann.filter().isEmpty()) {
            mq.filter(render(ann.filter(), vars, true, methodName));
        }
        if (!ann.distinct().isEmpty()) {
            mq.distinct(ann.distinct());
        }
        List<String> sort = new ArrayList<>(nameOrders);
        Pageable pageable = pageableIndex >= 0 && args.length > pageableIndex
                ? (args[pageableIndex] instanceof Pageable p ? p : null) : null;
        Sort directSort = pageableIndex >= 0 && args.length > pageableIndex
                && args[pageableIndex] instanceof Sort s ? s : null;
        if (pageable != null) {
            mq.page(pageable.getPageNumber() + 1).hitsPerPage(pageable.getPageSize());
            appendSort(sort, pageable.getSort(), entity, methodName);
        } else {
            appendSort(sort, directSort, entity, methodName);
            if (top != null) {
                mq.limit(top);
            }
        }
        if (!sort.isEmpty()) {
            mq.sort(sort.toArray(new String[0]));
        }
        MeiliSearchResult<?> result = operations.search(mq, entity.getType());
        List<?> hits = result.getHits();
        switch (shape) {
            case "LIST":
                return List.copyOf(hits);
            case "OPTIONAL": {
                if (hits.size() > 1) {
                    log.debug("Optional query matched {} hits, returning the first (method {})", hits.size(), methodName);
                }
                return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
            }
            default: {
                Long total = result.getEstimatedTotalHits() != null
                        ? result.getEstimatedTotalHits() : result.getTotalHits();
                return new PageImpl<>(List.copyOf(hits),
                        pageable != null ? pageable : Pageable.ofSize(Math.max(1, hits.size())),
                        total != null ? total : hits.size());
            }
        }
    }

    /**
     * Substitutes placeholders in one template.
     *
     * @param template   raw template text
     * @param vars       SpEL variable map (names + argN)
     * @param filterMode render values as filter literals (auto-quote/escape)
     * @param methodName for diagnostics
     * @return rendered text
     */
    private static String render(String template, Map<String, Object> vars, boolean filterMode,
                                 String methodName) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '#' && template.startsWith("#{", i)) {
                int end = template.indexOf('}', i);
                if (end < 0) {
                    throw new MeiliRepositoryConfigurationException(
                            "Template #{{ is not closed: " + methodName);
                }
                Object v = eval(template.substring(i + 2, end), vars, methodName);
                out.append(asText(v, filterMode, methodName));
                i = end + 1;
            } else if (c == ':' && i + 1 < template.length() && Character.isJavaIdentifierStart(template.charAt(i + 1))) {
                int j = i + 1;
                while (j < template.length() && Character.isJavaIdentifierPart(template.charAt(j))) {
                    j++;
                }
                String name = template.substring(i + 1, j);
                if (!vars.containsKey(name)) {
                    throw new MeiliRepositoryConfigurationException("Placeholder :" + name
                            + " has no bindable parameter (bindable: " + vars.keySet() + ", method " + methodName + ")");
                }
                out.append(asText(vars.get(name), filterMode, methodName));
                i = j;
            } else if (c == '?' && i + 1 < template.length() && Character.isDigit(template.charAt(i + 1))) {
                int j = i + 1;
                while (j < template.length() && Character.isDigit(template.charAt(j))) {
                    j++;
                }
                int n = Integer.parseInt(template.substring(i + 1, j));
                if (!vars.containsKey("arg" + n)) {
                    throw new MeiliRepositoryConfigurationException("Placeholder ?" + n
                            + " out of range (value parameter count " + countArgVars(vars) + ", method " + methodName + ")");
                }
                out.append(asText(vars.get("arg" + n), filterMode, methodName));
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Counts positional bindings.
     *
     * @param vars evaluation variables
     * @return number of positional (argN) bindings
     */
    private static int countArgVars(Map<String, Object> vars) {
        return (int) vars.keySet().stream().filter(k -> k.startsWith("arg")).count();
    }

    /**
     * Evaluates a raw SpEL expression against bound variables.
     *
     * @param expression expression body
     * @param vars       variables
     * @param methodName diagnostics
     * @return evaluation result
     */
    private static Object eval(String expression, Map<String, Object> vars, String methodName) {
        try {
            SimpleEvaluationContext.Builder builder = SimpleEvaluationContext.forReadOnlyDataBinding();
            SimpleEvaluationContext ctx = builder.withInstanceMethods().build();
            vars.forEach(ctx::setVariable);
            return PARSER.parseExpression(expression).getValue(ctx);
        } catch (RuntimeException e) {
            throw new MeiliOrmException("SpEL evaluation failed: #{" + expression + "} (method "
                    + methodName + "): " + e.getMessage(), e);
        }
    }

    /**
     * Renders a substituted value for its template context.
     *
     * @param value      substituted value
     * @param filterMode literal rendering mode for filter templates
     * @param methodName diagnostics
     * @return rendered text
     */
    private static String asText(Object value, boolean filterMode, String methodName) {
        if (value == null) {
            throw new MeiliRepositoryConfigurationException(
                    "Template parameter value is null (method " + methodName + ")");
        }
        return filterMode ? MeiliLiterals.of(value) : String.valueOf(value);
    }

    /**
     * Bootstrap placeholder resolvability (names present, indices in range).
     *
     * @param template   raw template
     * @param byName     bindable names
     * @param valueCount number of value parameters
     * @param methodName diagnostics
     */
    private static void validatePlaceholders(String template, Map<String, Integer> byName,
                                             int valueCount, String methodName) {
        Matcher named = NAMED.matcher(template);
        while (named.find()) {
            if (!byName.containsKey(named.group(1))) {
                throw new MeiliRepositoryConfigurationException("Placeholder :" + named.group(1)
                        + " has no bindable parameter (bindable: " + byName.keySet() + ", method " + methodName + ")");
            }
        }
        Matcher pos = POSITIONAL.matcher(template);
        while (pos.find()) {
            int n = Integer.parseInt(pos.group(1));
            if (n >= valueCount) {
                throw new MeiliRepositoryConfigurationException("Placeholder ?" + n + " out of range (value parameter count "
                        + valueCount + ", method " + methodName + ")");
            }
        }
    }

    /**
     * Paren balance ignoring double-quoted literals.
     *
     * @param filter   raw filter template
     * @param methodName diagnostics
     */
    private static void checkBrackets(String filter, String methodName) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < filter.length(); i++) {
            char c = filter.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (!inString && c == '(') {
                depth++;
            } else if (!inString && c == ')') {
                if (--depth < 0) {
                    throw new MeiliRepositoryConfigurationException(
                            "filter template parentheses are unbalanced (at character " + (i + 1) + "): " + methodName);
                }
            }
        }
        if (depth != 0) {
            throw new MeiliRepositoryConfigurationException(
                    "filter template parentheses are unbalanced (missing " + depth + " closing ')'): " + methodName);
        }
    }

    /**
     * Appends Pageable/Sort orders (bridged) after name orders.
     *
     * @param sort     accumulator
     * @param s        optional sort
     * @param entity   metamodel for bridging
     * @param methodName diagnostics
     */
    private static void appendSort(List<String> sort, Sort s, MeiliPersistentEntity entity,
                                   String methodName) {
        if (s == null) {
            return;
        }
        for (Sort.Order o : s) {
            String path = MeiliPropertyPaths.resolveDotted(entity.getType(),
                    "Pageable/Sort of " + methodName, o.getProperty());
            sort.add(path + ":" + (o.isDescending() ? "desc" : "asc"));
        }
    }

    /**
     * Sortable role pre-check for method-name OrderBy paths.
     *
     * @param path       bridged path
     * @param entity     metamodel
     * @param methodName diagnostics
     */
    private static void requireSortable(String path, MeiliPersistentEntity entity, String methodName) {
        for (var p : entity.getProperties()) {
            if (p.getJsonPath().equals(path) && p.isSortable()) {
                return;
            }
        }
        throw new io.github.lamspace.meili.core.exception.MeiliMappingException("Method " + methodName
                + " uses property " + path + " without declared sortable; fix: declare sortable = true on the field's @MeiliField, "
                + "or declare it server-side via @MeiliSetting passthrough (the entity declaration is this check's only source of truth)");
    }

    /**
     * Validates and labels the return shape.
     *
     * @param method repository method
     * @return shape label
     */
    private static String shapeOf(Method method) {
        Class<?> r = method.getReturnType();
        if (List.class.isAssignableFrom(r)) {
            return "LIST";
        }
        if (Optional.class.isAssignableFrom(r)) {
            return "OPTIONAL";
        }
        if (Page.class.isAssignableFrom(r)) {
            boolean hasPageable = java.util.Arrays.stream(method.getParameters())
                    .anyMatch(p -> Pageable.class.isAssignableFrom(p.getType()));
            if (!hasPageable) {
                throw new MeiliRepositoryConfigurationException(
                        "Methods returning Page must declare a Pageable parameter: " + method.getName());
            }
            return "PAGE";
        }
        throw new MeiliRepositoryConfigurationException(
                "@MeiliQuery supports only List/Optional/Page return types: " + method.getName()
                        + " -> " + r.getName());
    }
}
