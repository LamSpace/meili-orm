package io.github.lamspace.meili.repository.query;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.mapping.MeiliPersistentProperty;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.repository.exception.MeiliRepositoryConfigurationException;
import io.github.lamspace.meili.repository.query.MeiliMethodNames.Clause;
import io.github.lamspace.meili.repository.query.MeiliMethodNames.Group;
import io.github.lamspace.meili.repository.query.MeiliMethodNames.Keyword;
import io.github.lamspace.meili.repository.query.MeiliMethodNames.Parsed;
import io.github.lamspace.meili.repository.support.MeiliPropertyPaths;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Bootstrap-time resolver turning a derived-query method into an executable
 * {@link MeiliQueryHandler}.
 *
 * <p><b>Validation order (all at context startup, per capability contract).</b> grammar →
 * return shape → argument arity → property bridging (dictionary split + core projection) →
 * role pre-check (filter paths filterable, order paths sortable, CONTAINING targets
 * searchable). Failures name the method and the offending property; role failures carry
 * both fixes (field annotation or {@code @MeiliSetting} passthrough), and the entity
 * declaration — not server state — is the sole pre-check input.
 *
 * <p><b>Primary-key exemption.</b> Conditions targeting the document's own primary-key path
 * skip the filterable pre-check: the server can always address documents by primary key,
 * so demanding a declaration there would reject valid methods.
 *
 * <p><b>Runtime semantics.</b> An {@code IN} with an empty collection short-circuits to an
 * empty result without a server request. CONTAINING/LIKE become full-text {@code q=<value>}
 * restricted by {@code attributesToSearchOn} (at most one per method). Method-name OrderBy
 * precedes {@code Pageable}/{@code Sort} sorting; when a page parameter is present, TopN is
 * ignored (bootstrap WARN). {@code Page} totals follow the estimated-count contract.
 * Null condition arguments fail per-call via {@link MeiliLiterals} (a skipped condition
 * would silently change query meaning).
 */
public final class MeiliDerivedQueries {

    /** Diagnostics for Optional-with-multiple-hits truncation. */
    private static final Logger log = LoggerFactory.getLogger(MeiliDerivedQueries.class);

    /**
     * Resolved clause bound to absolute method-argument indices (second only for BETWEEN).
     *
     * @param negate    NOT-wrapping flag
     * @param keyword   comparison/search keyword
     * @param path      bridged document path
     * @param argIndex  absolute index of the value argument, -1 when the keyword takes none
     * @param secondArg absolute index of BETWEEN's upper bound, -1 otherwise
     */
    private record BoundClause(boolean negate, Keyword keyword, String path, int argIndex, int secondArg) {
    }

    /**
     * Resolved order term.
     *
     * @param path bridged document path
     * @param asc  ascending flag
     */
    private record BoundOrder(String path, boolean asc) {
    }

    /** Supported return shapes. */
    private enum Shape {
        /** {@code List}. */
        LIST,
        /** {@code Optional}. */
        OPTIONAL,
        /** {@code Page}. */
        PAGE
    }

    /**
     * Method plan frozen at bootstrap.
     *
     * @param shape          return shape
     * @param top            TopN cap or {@code null}
     * @param groups         OR-groups of bound AND-clauses
     * @param orders         bound ORDER BY terms
     * @param pageableIndex  index of a Pageable/Sort parameter, -1 when absent
     * @param searchPath     document path of the single CONTAINING/LIKE target, {@code null} when none
     * @param searchArgIndex absolute argument index feeding the search term
     * @param domainType     entity class
     * @param indexName      bound index uid for diagnostics
     * @param methodName     method name for diagnostics
     */
    private record Plan(Shape shape, Integer top, List<List<BoundClause>> groups,
                        List<BoundOrder> orders, int pageableIndex, String searchPath,
                        int searchArgIndex, Class<?> domainType, String indexName,
                        String methodName) {
    }

    /** Utility — no instances. */
    private MeiliDerivedQueries() {
    }

    /**
     * Resolves one derived method at bootstrap.
     *
     * @param method     repository interface method (name in derived grammar)
     * @param entity     bound domain metamodel
     * @param operations template used at execution time
     * @return immutable handler; every validation failure throws before returning
     * @throws MeiliRepositoryConfigurationException grammar/shape/arity failures
     * @throws MeiliMappingException                  property-bridge and role-precheck failures
     */
    public static MeiliQueryHandler bootstrap(Method method, MeiliPersistentEntity entity,
                                              MeiliSearchOperations operations) {
        Parsed parsed = MeiliMethodNames.parse(method.getName());
        if (parsed.distinct()) {
            throw new MeiliRepositoryConfigurationException("v1 不支持方法名 Distinct 修饰符（Meili 的 distinct 必须指定属性）；"
                    + "请改用 @MeiliQuery(distinct = \"…\")：方法 " + method.getName());
        }
        Shape shape = returnShape(method);
        Class<?> domainType = entity.getType();
        String methodName = method.getName();

        List<Integer> valueArgs = new ArrayList<>();
        int pageableIndex = -1;
        var params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i].getType();
            if (Pageable.class.isAssignableFrom(p) || Sort.class.isAssignableFrom(p)) {
                pageableIndex = i;
            } else {
                valueArgs.add(i);
            }
        }
        if (parsed.expectedValueArguments() != valueArgs.size()) {
            throw new MeiliRepositoryConfigurationException("方法 " + methodName + " 的值参数数量与派生条件不符: 需要 "
                    + parsed.expectedValueArguments() + " 个，实际 " + valueArgs.size());
        }
        if (pageableIndex >= 0 && parsed.maxResults() != null) {
            log.warn("方法 {} 同时声明 TopN 与分页参数：TopN 被忽略，以分页为准", methodName);
        }

        String idPath = entity.getIdProperty().getJsonPath();
        List<List<BoundClause>> groups = new ArrayList<>();
        int[] slot = {0};
        for (Group g : parsed.groups()) {
            List<BoundClause> bound = new ArrayList<>();
            for (Clause c : g.clauses()) {
                List<String> segments = MeiliPropertyPaths.splitCamel(domainType, methodName,
                        String.join(".", c.chain()));
                String path = MeiliPropertyPaths.resolveChain(domainType, methodName, segments);
                precheckRole(c.keyword(), path, entity, methodName, idPath);
                int first = c.keyword() == Keyword.TRUE || c.keyword() == Keyword.FALSE
                        ? -1 : valueArgs.get(slot[0]);
                int second = c.keyword() == Keyword.BETWEEN ? valueArgs.get(slot[0] + 1) : -1;
                bound.add(new BoundClause(c.negate(), c.keyword(), path, first, second));
                slot[0] += switch (c.keyword()) {
                    case BETWEEN -> 2;
                    case TRUE, FALSE -> 0;
                    default -> 1;
                };
            }
            groups.add(bound);
        }
        String searchPath = null;
        int searchArg = -1;
        for (List<BoundClause> gs : groups) {
            for (BoundClause c : gs) {
                if (c.keyword() == Keyword.CONTAINING || c.keyword() == Keyword.LIKE) {
                    if (searchPath != null) {
                        throw new MeiliRepositoryConfigurationException(
                                "v1 仅允许一个 Containing/Like 条件（全文 q 唯一）: " + methodName);
                    }
                    searchPath = c.path();
                    searchArg = c.argIndex();
                }
            }
        }
        List<BoundOrder> orders = new ArrayList<>();
        for (MeiliMethodNames.Order o : parsed.orders()) {
            List<String> segments = MeiliPropertyPaths.splitCamel(domainType, methodName,
                    String.join(".", o.chain()));
            String path = MeiliPropertyPaths.resolveChain(domainType, methodName, segments);
            requireRole(path, MeiliPersistentProperty::isSortable, entity, methodName, "sortable");
            orders.add(new BoundOrder(path, o.asc()));
        }
        Plan plan = new Plan(shape, parsed.maxResults(), groups, orders, pageableIndex,
                searchPath, searchArg, domainType, entity.getIndexName(), methodName);
        return args -> execute(plan, args, operations);
    }

    /**
     * Binds arguments, renders the DSL and adapts the result shape.
     *
     * @param plan       frozen bootstrap plan
     * @param args       runtime arguments
     * @param operations template for execution
     * @return adapted {@code List}/{@code Optional}/{@code Page} result
     */
    private static Object execute(Plan plan, Object[] args, MeiliSearchOperations operations) {
        for (List<BoundClause> gs : plan.groups()) {
            for (BoundClause c : gs) {
                if (c.keyword() == Keyword.IN && isEmptyCollection(args[c.argIndex()])) {
                    return empty(plan, args);
                }
            }
        }
        List<List<String>> rendered = new ArrayList<>();
        for (List<BoundClause> gs : plan.groups()) {
            List<String> andParts = new ArrayList<>();
            for (BoundClause c : gs) {
                if (c.keyword() == Keyword.CONTAINING || c.keyword() == Keyword.LIKE) {
                    continue;
                }
                andParts.add(renderClause(c, args, plan.methodName()));
            }
            if (!andParts.isEmpty()) {
                rendered.add(andParts);
            }
        }
        // parentheses only when OR-ing: a lone AND-chain reads exactly as written
        List<String> groupExprs = new ArrayList<>();
        for (List<String> andParts : rendered) {
            groupExprs.add(rendered.size() > 1 && andParts.size() > 1
                    ? "(" + String.join(" AND ", andParts) + ")"
                    : String.join(" AND ", andParts));
        }
        MeiliQuery mq = MeiliQuery.query(
                plan.searchPath() == null ? null : argOf(args, plan.searchArgIndex(), plan));
        if (!groupExprs.isEmpty()) {
            mq.filter(String.join(" OR ", groupExprs));
        }
        if (plan.searchPath() != null) {
            mq.attributesToSearchOn(plan.searchPath());
        }
        List<String> sort = new ArrayList<>();
        for (BoundOrder o : plan.orders()) {
            sort.add(o.path() + ":" + (o.asc() ? "asc" : "desc"));
        }
        Pageable pageable = null;
        Sort extraSort = null;
        if (plan.pageableIndex() >= 0) {
            Object p = args.length > plan.pageableIndex() ? args[plan.pageableIndex()] : null;
            if (p instanceof Pageable pg) {
                pageable = pg;
            } else if (p instanceof Sort s) {
                extraSort = s;
            }
        }
        if (pageable != null) {
            mq.page(pageable.getPageNumber() + 1).hitsPerPage(pageable.getPageSize());
            appendSort(sort, pageable.getSort(), plan);
        } else {
            appendSort(sort, extraSort, plan);
            if (plan.top() != null) {
                mq.limit(plan.top());
            }
        }
        if (!sort.isEmpty()) {
            mq.sort(sort.toArray(new String[0]));
        }
        MeiliSearchResult<?> result = operations.search(mq, plan.domainType());
        List<?> hits = result.getHits();
        return switch (plan.shape()) {
            case LIST -> List.copyOf(hits);
            case OPTIONAL -> {
                if (hits.size() > 1) {
                    log.debug("Optional 查询命中 {} 条，返回首条（方法 {}）", hits.size(), plan.methodName());
                }
                yield hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
            }
            case PAGE -> {
                Long total = result.getEstimatedTotalHits() != null
                        ? result.getEstimatedTotalHits() : result.getTotalHits();
                yield new PageImpl<>(List.copyOf(hits),
                        pageable != null ? pageable : Pageable.ofSize(Math.max(1, hits.size())),
                        total != null ? total : hits.size());
            }
        };
    }

    /**
     * Reads the CONTAINING/LIKE text argument (null rejected — it would silently drop q).
     *
     * @param args runtime arguments
     * @param index absolute argument index
     * @param plan frozen plan for diagnostics
     * @return full-text query string
     */
    private static String argOf(Object[] args, int index, Plan plan) {
        Object v = args[index];
        if (v == null) {
            throw new IllegalArgumentException("方法 " + plan.methodName()
                    + " 的全文条件参数不允许为 null");
        }
        return String.valueOf(v);
    }

    /**
     * Renders one bound clause to filter DSL.
     *
     * @param c         bound clause
     * @param args      runtime arguments
     * @param methodName method name for diagnostics
     * @return DSL fragment
     */
    private static String renderClause(BoundClause c, Object[] args, String methodName) {
        Object value = c.argIndex() >= 0 ? args[c.argIndex()] : null; // TRUE/FALSE 无值槽
        String expr = switch (c.keyword()) {
            case EQ -> c.path + " = " + MeiliLiterals.of(value);
            case NE -> c.path + " != " + MeiliLiterals.of(value);
            case GT -> c.path + " > " + MeiliLiterals.of(value);
            case GTE -> c.path + " >= " + MeiliLiterals.of(value);
            case LT -> c.path + " < " + MeiliLiterals.of(value);
            case LTE -> c.path + " <= " + MeiliLiterals.of(value);
            case BETWEEN -> c.path + " BETWEEN " + MeiliLiterals.of(value) + " AND "
                    + MeiliLiterals.of(args[c.secondArg()]);
            case IN -> c.path + " IN [" + literalsOf(value, methodName) + "]";
            case TRUE -> c.path + " = true";
            case FALSE -> c.path + " = false";
            default -> throw new IllegalStateException(c.keyword().name());
        };
        return c.negate() ? "NOT (" + expr + ")" : expr;
    }

    /**
     * Tests an IN argument for emptiness (short-circuit condition).
     *
     * @param v candidate collection/array argument
     * @return whether an IN argument is an empty collection or array
     */
    private static boolean isEmptyCollection(Object v) {
        return (v instanceof Collection<?> c && c.isEmpty()) || (v instanceof Object[] a && a.length == 0);
    }

    /**
     * Renders an IN-list from a collection/array of scalar values.
     *
     * @param collectionOrArray membership argument
     * @param methodName        method name for diagnostics
     * @return comma-joined literals
     */
    private static String literalsOf(Object collectionOrArray, String methodName) {
        List<Object> items = new ArrayList<>();
        if (collectionOrArray instanceof Collection<?> col) {
            items.addAll(col);
        } else if (collectionOrArray instanceof Object[] arr) {
            items.addAll(List.of(arr));
        } else {
            throw new IllegalArgumentException("方法 " + methodName + " 的 In 条件参数必须是集合或数组: "
                    + collectionOrArray.getClass().getName());
        }
        List<String> out = new ArrayList<>();
        for (Object item : items) {
            out.add(MeiliLiterals.of(item));
        }
        return String.join(", ", out);
    }

    /**
     * Appends a Pageable/Sort-derived sort list after method-name orders.
     *
     * @param sort accumulator
     * @param s    optional sort (skipped when null)
     * @param plan frozen plan for bridging context
     */
    private static void appendSort(List<String> sort, Sort s, Plan plan) {
        if (s == null) {
            return;
        }
        for (Sort.Order o : s) {
            String path = MeiliPropertyPaths.resolveDotted(plan.domainType(),
                    plan.methodName() + " 的 Pageable/Sort", o.getProperty());
            sort.add(path + ":" + (o.isDescending() ? "desc" : "asc"));
        }
    }

    /**
     * Builds the short-circuit empty return for an empty IN argument.
     *
     * @param plan frozen plan
     * @param args runtime arguments (for the Pageable slot)
     * @return empty List/Optional/Page
     */
    private static Object empty(Plan plan, Object[] args) {
        Object p = plan.pageableIndex() >= 0 && args.length > plan.pageableIndex()
                ? args[plan.pageableIndex()] : null;
        return switch (plan.shape()) {
            case LIST -> List.of();
            case OPTIONAL -> Optional.empty();
            case PAGE -> new PageImpl<>(List.of(),
                    p instanceof Pageable pg ? pg : Pageable.ofSize(20), 0);
        };
    }

    /**
     * Role pre-check per keyword family.
     *
     * @param keyword    clause keyword
     * @param path       bridged document path
     * @param entity     domain metamodel providing role flags
     * @param methodName method name for error location
     * @param idPath     primary-key path (filterable-exempt)
     */
    private static void precheckRole(Keyword keyword, String path, MeiliPersistentEntity entity,
                                     String methodName, String idPath) {
        switch (keyword) {
            case CONTAINING, LIKE -> requireRole(path, MeiliPersistentProperty::isSearchable,
                    entity, methodName, "searchable");
            default -> {
                if (!path.equals(idPath)) {
                    requireRole(path, MeiliPersistentProperty::isFilterable, entity, methodName,
                            "filterable");
                }
            }
        }
    }

    /**
     * Asserts a role flag on the metamodel property owning the path.
     *
     * @param path       bridged document path
     * @param flag       required role predicate
     * @param entity     domain metamodel
     * @param methodName method name for error location
     * @param roleName   role label used in the message
     * @throws io.github.lamspace.meili.core.exception.MeiliMappingException when undeclared
     */
    private static void requireRole(String path,
                                    java.util.function.Predicate<MeiliPersistentProperty> flag,
                                    MeiliPersistentEntity entity, String methodName, String roleName) {
        for (MeiliPersistentProperty p : entity.getProperties()) {
            if (p.getJsonPath().equals(path) && flag.test(p)) {
                return;
            }
        }
        throw new MeiliMappingException("方法 " + methodName + " 的属性 " + path + " 未声明 " + roleName
                + "；修复：在该字段的 @MeiliField 上声明 " + roleName + " = true，"
                + "或经 @MeiliSetting 透传在服务端声明（实体声明是本校验的唯一判定源）");
    }

    /**
     * Validates and maps the method return type to a supported shape.
     *
     * @param method repository method
     * @return return shape
     */
    private static Shape returnShape(Method method) {
        Class<?> r = method.getReturnType();
        if (List.class.isAssignableFrom(r)) {
            return Shape.LIST;
        }
        if (Optional.class.isAssignableFrom(r)) {
            return Shape.OPTIONAL;
        }
        if (Page.class.isAssignableFrom(r)) {
            boolean hasPageable = java.util.Arrays.stream(method.getParameters())
                    .anyMatch(p -> Pageable.class.isAssignableFrom(p.getType()));
            if (!hasPageable) {
                throw new MeiliRepositoryConfigurationException("返回 Page 的方法必须声明 Pageable 参数: "
                        + method.getName());
            }
            return Shape.PAGE;
        }
        throw new MeiliRepositoryConfigurationException(
                "v1 派生查询仅支持 List/Optional/Page 返回类型（不支持投影/Stream/基本类型）: "
                        + method.getName() + " -> " + r.getName());
    }
}
