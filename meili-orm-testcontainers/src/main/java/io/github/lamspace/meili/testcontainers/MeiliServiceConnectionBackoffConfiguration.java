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
package io.github.lamspace.meili.testcontainers;

import java.util.ArrayList;
import java.util.List;

import io.github.lamspace.meili.autoconfigure.MeiliConnectionDetails;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

/**
 * Back-off guard making a user-declared {@link MeiliConnectionDetails} bean authoritative over
 * the {@code @ServiceConnection} bridge contributed by this module.
 *
 * <p>Why a post-processor is needed: Boot's service-connection registrar registers bridge beans
 * during test-context customization — before any {@code @Bean} method of the test's
 * configuration classes has been parsed — so the registrar's "existing beans" check cannot see a
 * user-declared details bean. Left alone, both definitions coexist and by-type injection of
 * {@link MeiliConnectionDetails} becomes ambiguous instead of preferring the user's bean. The
 * declared contract (see the module documentation) is: when the user supplies a details bean,
 * the bridge retires.
 *
 * <p>Identification: the registrar tags every bridge-contributed definition with the
 * {@link ServiceConnection} annotation class name as a bean-definition attribute; this guard
 * removes exactly the tagged definitions when at least one untagged
 * {@link MeiliConnectionDetails} definition (the user's) also exists. Untagged definitions are
 * never touched. Boot's own properties-backed default is untagged by nature, and it only exists
 * when nothing else did — by the time this guard runs the default is already retired by the
 * conditional it carries, so there is nothing here to preserve.
 *
 * <p>Ordering and lifecycle: runs as a regular {@link BeanFactoryPostProcessor}, i.e. after all
 * configuration classes (user and auto-configured) have been parsed and before any bean
 * instantiation; once per context, and a hypothetical second pass would find nothing to do
 * (idempotent by branch).
 */
@AutoConfiguration
public class MeiliServiceConnectionBackoffConfiguration {

    /**
     * Instantiated by the auto-configuration machinery.
     */
    public MeiliServiceConnectionBackoffConfiguration() {
    }

    /**
     * Contributes the bridge-retirement pass. Declared {@code static} so the post-processor is
     * instantiated without pre-creating this configuration class, keeping it eligible for the
     * earliest post-processor phase.
     *
     * @return the back-off post-processor
     */
    @Bean
    static BeanFactoryPostProcessor meiliServiceConnectionBackoffPostProcessor() {
        return new MeiliServiceConnectionBackoffPostProcessor();
    }

    /**
     * Removes {@code @ServiceConnection}-contributed {@link MeiliConnectionDetails} definitions
     * when a user-declared one exists alongside them.
     */
    private static final class MeiliServiceConnectionBackoffPostProcessor implements BeanFactoryPostProcessor {

        /**
         * Stateless post-processor; all inputs arrive through the callback argument.
         */
        private MeiliServiceConnectionBackoffPostProcessor() {
        }

        /**
         * Single retirement pass over the post-parsing bean definition set.
         *
         * <p>Decision order: collect bridge-tagged names first; if none exist or all existing
         * {@link MeiliConnectionDetails} definitions are bridge-tagged (no user definition),
         * return without touching anything; otherwise remove every tagged definition via the
         * bean factory's own {@link BeanDefinitionRegistry} view of the context.
         *
         * @param beanFactory the fully-parsed bean factory to inspect
         * @throws BeansException never thrown directly by this implementation
         */
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            String[] names = beanFactory.getBeanNamesForType(MeiliConnectionDetails.class);
            List<String> bridgeNames = new ArrayList<>();
            for (String name : names) {
                if (beanFactory.getBeanDefinition(name).getAttribute(ServiceConnection.class.getName()) != null) {
                    bridgeNames.add(name);
                }
            }
            if (bridgeNames.isEmpty() || bridgeNames.size() == names.length) {
                return;
            }
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            bridgeNames.forEach(registry::removeBeanDefinition);
        }
    }
}
