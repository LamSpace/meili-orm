package io.github.lamspace.meili.repository;

import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.repository.core.MeiliRepositoryProxy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.data.repository.Repository;

/**
 * Produces one {@link MeiliRepository} proxy per repository interface as a Spring
 * {@link FactoryBean}.
 *
 * <p><b>Deliberate shape.</b> This class implements {@code FactoryBean} directly instead of
 * extending commons' {@code RepositoryFactoryBeanSupport}: that base class's method surface
 * differs between the commons generations this module must run on (binary-diff evidence in
 * the change spike record), so extending it would break single-jar dual-generation
 * compatibility. Dependency lookup is done explicitly at {@link #afterPropertiesSet()} —
 * the operations template and metamodel cache are resolved by type from the owning bean
 * factory, which is exactly the pair the data auto-configuration guarantees when enabled.
 *
 * <p><b>Lifecycle.</b> The proxy is built once during context refresh and cached;
 * {@link #getObject()} is idempotent and the repository is a context singleton.
 *
 * @param <T>  domain type (resolved from the interface's generics at build time)
 * @param <ID> primary-key type
 * @param <R>  repository interface type
 */
public class MeiliRepositoryFactoryBean<T, ID, R extends Repository<T, ID>>
        implements FactoryBean<R>, BeanFactoryAware, InitializingBean {

    /** User repository interface this bean proxies. */
    private final Class<?> repositoryInterface;
    /** Owning bean factory for by-type lookups. */
    private BeanFactory beanFactory;
    /** Built proxy, frozen after {@link #afterPropertiesSet()}. */
    private R repository;

    /**
     * @param repositoryInterface the user repository interface; required
     */
    public MeiliRepositoryFactoryBean(Class<?> repositoryInterface) {
        this.repositoryInterface = repositoryInterface;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        MeiliSearchOperations operations;
        MeiliMappingContext context;
        try {
            operations = beanFactory.getBean(MeiliSearchOperations.class);
            context = beanFactory.getBean(MeiliMappingContext.class);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalStateException("MeiliRepository 需要容器中的 MeiliSearchOperations/"
                    + "MeiliMappingContext bean：请确认已引入 spring-boot-starter-meili-orm 且 meili.enabled=true"
                    + "（仓库接口: " + repositoryInterface.getName() + "）", e);
        }
        @SuppressWarnings("unchecked")
        R built = (R) MeiliRepositoryProxy.create(repositoryInterface, operations, context);
        this.repository = built;
    }

    @Override
    public R getObject() {
        return repository;
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
