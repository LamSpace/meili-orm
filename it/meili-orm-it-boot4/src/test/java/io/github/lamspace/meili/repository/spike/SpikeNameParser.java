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
 * spike 原型：自研派生查询方法名解析器（结构桥路线被 commons 4.0 的
 * {@code Part.getProperty()} 返回类型代际迁移证伪后的替代形态）。
 * 语法（动词/By/top/OrderBy/And/Or/Not/关键字尾缀）自研，属性段用实体反射字典
 * 做最长前缀切分（不支持缩写）；语义仍由 {@link SpikePropertyResolver} 落 core 投影名。
 */
final class SpikeNameParser {

    /** 关键字 → 内部规范名；按长度降序尝试剥离。 */
    private static final Map<String, String> SUFFIX_KEYWORDS = new HashMap<>();
    /** 不支持面关键字（v1 直接拒绝）。 */
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

    /** 一个条件子句。 */
    record Clause(boolean or, boolean negate, String keyword, List<String> chain) {
    }

    /** 一个排序子句。 */
    record Order(List<String> chain, boolean asc) {
    }

    /** 解析结果。 */
    record Parsed(Integer maxResults, boolean distinct, List<Clause> clauses, List<Order> orders) {
    }

    /**
     * 解析派生查询方法名。
     *
     * @param domainType 实体类（属性字典来源）
     * @param methodName 仓库接口方法名
     * @return 结构化解析结果
     * @throws IllegalArgumentException 语法非法或属性段无法解析
     * @throws UnsupportedOperationException v1 不支持关键字
     */
    static Parsed parse(Class<?> domainType, String methodName) {
        Matcher m = METHOD.matcher(methodName);
        if (!m.matches() || m.group(4) == null) {
            throw new IllegalArgumentException("方法名无法解析: " + methodName);
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

    /** 按大写 And/Or 定界切分（保持出现顺序）。 */
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

    /** 子句后的连接词（and 默认；or 影响下一子句）。 */
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
                throw new UnsupportedOperationException("v1 不支持关键字: " + kw + "（方法名片段 " + seg + "）");
            }
        }
    }

    /** 属性字典最长前缀切分：AuthorCity → [author, city]；缩写不支持。 */
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
                throw new IllegalArgumentException("无法解析属性段（不支持缩写）: " + path
                        + " @ " + current.getSimpleName());
            }
            chain.add(match.getName());
            pos += match.getName().length();
            current = match.getType();
        }
        return chain;
    }

    /** 当前类型可查询字段集合（含被排除字段以便给出精确错误）。 */
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
