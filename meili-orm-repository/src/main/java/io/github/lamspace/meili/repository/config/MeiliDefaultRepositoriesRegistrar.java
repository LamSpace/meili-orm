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
            log.debug("未发现 @AutoConfigurationPackage：跳过仓库兜底扫描（非 Boot 应用请用 @EnableMeiliRepositories）");
            return;
        }
        List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
        MeiliRepositoryScanner.scan(packages, registry);
    }
}
