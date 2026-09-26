package io.github.lamspace.meili.repository.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliNames;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * spike 原型：结构桥的属性段解析器。只吃"Java 属性段名字符串"（来自
 * {@code Part.getPropertyParts()}），完全绕开 commons 的
 * {@code PropertyPath}/{@code PersistentPropertyPath} 属性模型，用反射 +
 * core 的 {@link MeiliNames#docName} 逐段拼出文档点路径——证明"语法归 commons、
 * 语义归 core"的单源码双代可行性。正式实现按同规则落 src/main。
 */
final class SpikePropertyResolver {

    /** 最大嵌套深度，与 core 展平深度一致。 */
    private static final int MAX_DEPTH = 3;

    private SpikePropertyResolver() {
    }

    /**
     * 将 Java 属性段链解析为文档点路径。
     *
     * @param rootType 实体类
     * @param segments 方法名解析出的 Java 属性段（如 ["author","city"]）
     * @return 文档路径（如 {@code author.city}）
     * @throws IllegalArgumentException 任一解析失败（未知段/被排除段/链断在叶上）
     */
    static String resolve(Class<?> rootType, String[] segments) {
        if (segments == null || segments.length == 0 || segments.length > MAX_DEPTH + 1) {
            throw new IllegalArgumentException("非法属性段链: " + String.join(".",
                    segments == null ? new String[0] : segments));
        }
        List<String> docSegments = new ArrayList<>(segments.length);
        Class<?> current = rootType;
        for (int i = 0; i < segments.length; i++) {
            Field field = findField(current, segments[i]);
            boolean last = i == segments.length - 1;
            docSegments.add(MeiliNames.docName(field, field.getName()));
            if (!last) {
                if (MeiliNames.isSimpleType(field.getType())) {
                    throw new IllegalArgumentException("属性链在简单类型上中断: "
                            + current.getSimpleName() + "." + field.getName());
                }
                current = field.getType();
            } else if (!MeiliNames.isSimpleType(field.getType())) {
                throw new IllegalArgumentException("聚合属性不能作为查询目标: "
                        + current.getSimpleName() + "." + field.getName());
            }
        }
        return String.join(".", docSegments);
    }

    /** 按 Java 名（首字母大小写不敏感）沿类层级查找可查询字段。 */
    private static Field findField(Class<?> type, String javaName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase(javaName)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                            || f.isAnnotationPresent(JsonIgnore.class)) {
                        throw new IllegalArgumentException("属性不可查询（static/synthetic/@JsonIgnore）: "
                                + c.getSimpleName() + "." + f.getName());
                    }
                    return f;
                }
            }
        }
        throw new IllegalArgumentException("实体无此 Java 属性: " + type.getSimpleName() + "." + javaName);
    }
}
