package io.github.lamspace.meili.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 常驻零泄漏守卫：core 公开 API（{@code io.github.lamspace.meili.core} 下非
 * {@code internal} 包）的任何 public 类型/成员签名都不得引用官方客户端类型
 * （{@code com.meilisearch.sdk.*}）。泄漏即红，替代人工出口审查。
 */
class PublicApiLeakageGuardTest {

    private static final String CORE_PACKAGE = "io.github.lamspace.meili.core";
    private static final String INTERNAL_PACKAGE = CORE_PACKAGE + ".internal";
    private static final String FORBIDDEN = "com.meilisearch.sdk";

    @Test
    @DisplayName("公开面签名全量扫描：无官方客户端类型泄漏")
    void publicApiNeverReferencesClientTypes() throws Exception {
        List<Class<?>> exported = discoverExportedTypes();
        // 防空跑：守卫本身失效（包发现为 0 类）也算红
        assertThat(exported).as("发现的核心公开类型数").hasSizeGreaterThan(15);

        List<String> leaks = new ArrayList<>();
        for (Class<?> type : exported) {
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                check(leaks, type, ctor.getName(), ctor);
            }
            for (Method method : type.getDeclaredMethods()) {
                check(leaks, type, method.getName(), method);
            }
            for (Field field : type.getDeclaredFields()) {
                check(leaks, type, field.getName(), field);
            }
        }
        assertThat(leaks).isEmpty();
    }

    /**
     * Adds a leak entry if the member is public and its signature mentions the forbidden
     * package.
     *
     * @param leaks  accumulator
     * @param type   declaring type
     * @param name   member name for the report
     * @param member the member to inspect
     */
    private static void check(List<String> leaks, Class<?> type, String name, Object member) {
        int mods = member instanceof Constructor<?> c ? c.getModifiers()
                : member instanceof Method m ? m.getModifiers()
                : ((Field) member).getModifiers();
        if (!Modifier.isPublic(mods)) {
            return;
        }
        if (signatureOf((Member) member).contains(FORBIDDEN)) {
            leaks.add(type.getName() + "#" + name);
        }
    }

    /**
     * Renders the leak-relevant slice of a member's signature.
     *
     * @param m member to render
     * @return text containing return/parameter/field types and thrown types
     */
    private static String signatureOf(Member m) {
        StringBuilder sb = new StringBuilder(m instanceof Method me ? me.toGenericString()
                : m instanceof Constructor<?> ct ? ct.toGenericString()
                : ((Field) m).toGenericString());
        if (m instanceof Method method) {
            sb.append(' ').append(method.getReturnType().getName());
            for (Class<?> p : method.getParameterTypes()) {
                sb.append(' ').append(p.getName());
            }
            for (Class<?> t : method.getExceptionTypes()) {
                sb.append(' ').append(t.getName());
            }
        } else if (m instanceof Constructor<?> ctor) {
            for (Class<?> p : ctor.getParameterTypes()) {
                sb.append(' ').append(p.getName());
            }
        } else if (m instanceof Field field) {
            sb.append(' ').append(field.getType().getName());
        }
        return sb.toString();
    }

    /**
     * Enumerates runtime classes of the core package tree from {@code target/classes},
     * excluding the {@code internal} subtree.
     *
     * @return loadable, public, non-nested-local classes of the exported surface
     * @throws Exception on discovery failure
     */
    private static List<Class<?>> discoverExportedTypes() throws Exception {
        URL root = MeiliMappingContextProbe.classPathRoot();
        Path classes = new File(root.toURI()).toPath();
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classes)) {
            List<Path> files = walk
                    .filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> {
                        String name = classes.relativize(p).toString()
                                .replace(File.separatorChar, '.');
                        return name.startsWith(CORE_PACKAGE)
                                && !name.startsWith(INTERNAL_PACKAGE + ".")
                                && !name.equals(INTERNAL_PACKAGE + ".class");
                    })
                    .toList();
            for (Path p : files) {
                String binary = classes.relativize(p).toString()
                        .replaceAll("\\.class$", "")
                        .replace(File.separatorChar, '.');
                if (binary.endsWith("package-info")) {
                    continue;
                }
                Class<?> loaded = Class.forName(binary, false,
                        MeiliMappingContextProbe.class.getClassLoader());
                if (Modifier.isPublic(loaded.getModifiers())) {
                    out.add(loaded);
                }
            }
        }
        return out;
    }

    /** Holder locating the main-classes root on the test classpath. */
    static final class MeiliMappingContextProbe {
        /** Utility probe holder. */
        private MeiliMappingContextProbe() {
        }

        /**
         * @return the {@code target/classes} URL containing compiled core classes
         */
        static URL classPathRoot() {
            return io.github.lamspace.meili.core.mapping.MeiliMappingContext.class
                    .getProtectionDomain().getCodeSource().getLocation();
        }
    }
}
