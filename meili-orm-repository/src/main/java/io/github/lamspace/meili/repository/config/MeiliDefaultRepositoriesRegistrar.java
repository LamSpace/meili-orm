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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Package-less enable path used by {@link MeiliRepositoriesAutoConfiguration}: registers the
 * repositories found under the auto-configuration packages (the Boot application's own
 * packages, contributed by {@code @AutoConfigurationPackage}).
 *
 * <p><b>Absent marker tolerance.</b> Outside a Boot application there is no auto-configuration
 * package; in that case registration is skipped with a DEBUG log rather than failing — the
 * explicit {@code @EnableMeiliRepositories} path remains available for plain Spring usage.
 */
public class MeiliDefaultRepositoriesRegistrar
        implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    /** Public no-arg constructor required by Spring's registrar instantiation. */
    public MeiliDefaultRepositoriesRegistrar() {
    }

    /** Skip diagnostics logger. */
    private static final Logger log = LoggerFactory.getLogger(MeiliDefaultRepositoriesRegistrar.class);

    /** Owning bean factory, used to resolve auto-configuration packages. */
    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        if (!AutoConfigurationPackages.has(this.beanFactory)) {
            log.debug("No @AutoConfigurationPackage found: skipping the fallback repository scan (non-Boot applications should use @EnableMeiliRepositories)");
            return;
        }
        List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
        MeiliRepositoryScanner.scan(packages, registry);
    }
}
