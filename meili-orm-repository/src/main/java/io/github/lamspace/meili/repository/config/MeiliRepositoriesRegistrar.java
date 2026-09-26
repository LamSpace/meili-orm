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
package io.github.lamspace.meili.repository.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * Handles {@link EnableMeiliRepositories @EnableMeiliRepositories}: resolves the effective
 * base packages and delegates discovery to {@link MeiliRepositoryScanner}.
 *
 * <p><b>Package precedence.</b> Explicit {@code basePackages}/{@code value} entries and
 * {@code basePackageClasses} contributions union together; when all are empty the package of
 * the annotated class is used (Spring Data default semantics). Silent fallback to
 * "scan everything" is deliberately avoided — it would register repositories the user did
 * not ask for.
 */
public class MeiliRepositoriesRegistrar implements ImportBeanDefinitionRegistrar {

    /** Public no-arg constructor required by Spring's registrar instantiation. */
    public MeiliRepositoriesRegistrar() {
    }

    /**
     * Marker bean registered once per explicit {@code @EnableMeiliRepositories}. The
     * repository auto-configuration backs off when this marker is present, mirroring Spring
     * Boot's own {@code *RepositoriesRegistrar.EnabledConfiguration} pattern — an explicit
     * enable always wins over the fallback, regardless of bean-definition ordering.
     */
    static class EnabledConfiguration {

        /** Marker holder; instantiated only as a presence flag. */
        EnabledConfiguration() {
        }
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(EnabledConfiguration.class.getName())) {
            registry.registerBeanDefinition(EnabledConfiguration.class.getName(),
                    new org.springframework.beans.factory.support.RootBeanDefinition(EnabledConfiguration.class));
        }
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(EnableMeiliRepositories.class.getName());
        List<String> packages = new ArrayList<>();
        if (attributes != null) {
            for (String p : (String[]) attributes.getOrDefault("value", new String[0])) {
                packages.add(p);
            }
            for (String p : (String[]) attributes.getOrDefault("basePackages", new String[0])) {
                packages.add(p);
            }
            for (Class<?> c : (Class<?>[]) attributes.getOrDefault("basePackageClasses", new Class[0])) {
                packages.add(c.getPackageName());
            }
        }
        if (packages.isEmpty()) {
            packages.add(ClassUtils.getPackageName(metadata.getClassName()));
        }
        MeiliRepositoryScanner.scan(packages, registry);
    }
}
