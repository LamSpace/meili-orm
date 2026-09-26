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
package io.github.lamspace.meili.autoconfigure;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Classpath discovery of {@link MeiliDocument}-annotated entity types for a given set of base
 * packages.
 *
 * <p>Contract tier: pure function over package names. The result is deduplicated (overlapping
 * or repeated packages contribute each class once) and sorted by class name, so callers get a
 * stable order without depending on filesystem enumeration. An empty (or unresolvable) package
 * list yields an empty result — callers decide how to source packages; outside a Boot
 * application the right answer may well be "none".
 *
 * <p>A scanned candidate that cannot be loaded (broken classpath) fails fast with
 * {@link MeiliMappingException}: silently dropping entities would leave their indexes
 * uninitialized.
 */
public final class MeiliEntityScanner {

    /**
     * Utility holder; not instantiable.
     */
    private MeiliEntityScanner() {
    }

    /**
     * Scans the given base packages for {@link MeiliDocument} types.
     *
     * @param basePackages packages to search; {@code null} elements are rejected, an empty
     *                     list is accepted and yields an empty result
     * @return deduplicated entity classes sorted by class name; never {@code null}
     * @throws MeiliMappingException when a scanned candidate cannot be loaded
     */
    public static List<Class<?>> scanPackages(List<String> basePackages) {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(MeiliDocument.class));
        Set<Class<?>> found = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : provider.findCandidateComponents(basePackage)) {
                found.add(load(candidate));
            }
        }
        List<Class<?>> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparing(Class::getName));
        return List.copyOf(ordered);
    }

    /**
     * Materializes one scanned candidate through the scanner's own classloader.
     *
     * @param candidate the scanned bean definition
     * @return the loaded entity class
     * @throws MeiliMappingException when the class cannot be resolved or linked
     */
    private static Class<?> load(BeanDefinition candidate) {
        String className = candidate.getBeanClassName();
        try {
            return ClassUtils.forName(className, MeiliEntityScanner.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new MeiliMappingException(
                    "Scanned a @MeiliDocument candidate but it could not be loaded: " + className, e);
        }
    }
}
