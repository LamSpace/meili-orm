package io.github.lamspace.meili.repository.config;

import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.MeiliRepositoryFactoryBean;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.util.ClassUtils;

/**
 * Classpath discovery of {@link MeiliRepository} sub-interfaces and their registration as
 * {@link MeiliRepositoryFactoryBean} definitions.
 *
 * <p><b>Why self-owned rather than commons' registrar machinery.</b> The commons
 * repository-registration pipeline is built around factories extending commons' own
 * {@code RepositoryFactoryBeanSupport} (its hooks differ between the commons generations —
 * see the change spike record); registering our plain {@link FactoryBean} through it would
 * rely on property-setter contracts we do not declare. Scanning with stable spring-context
 * primitives keeps the enable-path free of generation-bound surface.
 *
 * <p><b>Selection rules.</b> A candidate is an independent, non-{@code @NoRepositoryBean}
 * interface (recursively) assignable to {@link MeiliRepository}. Each becomes a singleton
 * bean definition whose {@code factoryBeanObjectType} attribute is the interface itself, so
 * by-type injection resolves without early instantiation; the constructor argument is the
 * interface class and all wiring happens in the factory bean's lifecycle.
 *
 * <p><b>Idempotence.</b> Scanning twice (e.g. overlapping {@code @Enable} declarations)
 * skips a bean name that is already registered — last-wins is deliberately not applied to
 * avoid silently replacing an already-configured repository.
 */
final class MeiliRepositoryScanner {

    /** Utility — no instances. */
    private MeiliRepositoryScanner() {
    }

    /**
     * Scans the given base packages and registers repository definitions.
     *
     * @param basePackages packages to scan; blank entries ignored
     * @param registry     target bean definition registry
     * @throws IllegalStateException when a candidate class cannot be loaded
     */
    static void scan(List<String> basePackages, org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isInterface()
                                && beanDefinition.getMetadata().isIndependent()
                                && !beanDefinition.getMetadata().isAnnotated(NoRepositoryBean.class.getName());
                    }
                };
        provider.addIncludeFilter(new AssignableTypeFilter(MeiliRepository.class));
        for (String basePackage : basePackages) {
            if (basePackage == null || basePackage.isBlank()) {
                continue;
            }
            for (var candidate : provider.findCandidateComponents(basePackage)) {
                Class<?> iface = load(candidate.getBeanClassName());
                register(iface, registry);
            }
        }
    }

    /** Registers one interface (package-visible for the default auto-config path's reuse/tests). */
    static void register(Class<?> repositoryInterface,
                         org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
        String beanName = Introspector.decapitalize(ClassUtils.getShortName(repositoryInterface));
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        AbstractBeanDefinition definition = new RootBeanDefinition(MeiliRepositoryFactoryBean.class);
        definition.getConstructorArgumentValues().addGenericArgumentValue(repositoryInterface);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, repositoryInterface);
        registry.registerBeanDefinition(beanName, definition);
    }

    /** Normalizes candidate class names (handles inner-class $ forms) and loads. */
    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, MeiliRepositoryScanner.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("仓库接口无法加载: " + className, e);
        }
    }

    /** Convenience for callers holding an array form of packages. */
    static List<String> toList(String[] packages) {
        List<String> out = new ArrayList<>();
        for (String p : packages) {
            out.add(p);
        }
        return out;
    }
}
