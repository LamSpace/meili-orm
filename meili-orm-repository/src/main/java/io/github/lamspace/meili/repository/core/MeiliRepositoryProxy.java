package io.github.lamspace.meili.repository.core;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.query.MeiliAnnotatedQueries;
import io.github.lamspace.meili.repository.query.MeiliDerivedQueries;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.repository.Repository;

/**
 * Self-owned JDK-proxy machinery for {@link MeiliRepository} interfaces.
 *
 * <p><b>Why not commons' factory base classes.</b> Their protected extension hooks change
 * signature between the commons generations this module must run on (binary-diff evidence in
 * the change spike record), so subclassing them would bind one compiled jar to one
 * generation. Everything here stays on two-generation-stable surfaces: plain {@link Proxy}
 * plumbing, delegation to a bound {@link SimpleMeiliRepository}, and a dispatch table frozen
 * during {@link #create} — method-resolution failures are bootstrap errors, never first-call
 * surprises.
 *
 * <p><b>Dispatch contract.</b> For each interface method, in order:
 * <ol>
 *   <li>{@code Object} methods follow proxy identity semantics;</li>
 *   <li>user {@code default} methods run via {@link InvocationHandler#invokeDefault} so
 *       hand-written composition helpers behave exactly like on any interface;</li>
 *   <li>methods inherited from the Spring Data repository API execute on the bound
 *       {@link SimpleMeiliRepository} (CRUD/paging semantics);</li>
 *   <li>user abstract query methods go through the query-layer seam {@link #invokerFor}.</li>
 * </ol>
 *
 * <p><b>Thread model.</b> The dispatch table is fully populated before {@link #create}
 * returns and never mutated afterwards; the resulting proxy is immutable and safe for
 * concurrent use.
 */
public final class MeiliRepositoryProxy implements InvocationHandler {

    /** Per-method dispatch table; empty until the owning proxy exists, final after create. */
    private final Map<Method, MethodInvoker> dispatch = new HashMap<>();

    /** A resolved per-method executor. */
    interface MethodInvoker {
        /**
         * Executes the method against runtime arguments.
         *
         * @param args non-null argument array (possibly empty)
         * @return the method's return value
         * @throws Throwable any application exception propagates per the proxy contract
         */
        Object invoke(Object[] args) throws Throwable;
    }

    /** Owned instance; handlers are built exclusively through {@link #create}. */
    private MeiliRepositoryProxy() {
    }

    /**
     * Builds a repository proxy for one interface.
     *
     * @param repositoryInterface user repository interface (parameterized {@code MeiliRepository<T, ID>})
     * @param operations          template backing all data paths; required
     * @param context             shared entity-metamodel cache; required
     * @return a proxy implementing {@code repositoryInterface}
     * @throws MeiliMappingException   when the domain type cannot be derived or fails mapping
     * @throws UnsupportedOperationException when a user query method cannot be resolved
     */
    public static Object create(Class<?> repositoryInterface, MeiliSearchOperations operations,
                                MeiliMappingContext context) {
        Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(repositoryInterface, Repository.class);
        if (generics == null || generics.length < 2) {
            throw new MeiliMappingException("无法从仓库接口解析域类型（需显式参数化 MeiliRepository<T, ID>）: "
                    + repositoryInterface.getName());
        }
        Class<?> domainType = generics[0];
        MeiliPersistentEntity entity = context.getEntity(domainType);
        SimpleMeiliRepository<?, ?> delegate = new SimpleMeiliRepository<>(operations, entity);
        MeiliRepositoryProxy handler = new MeiliRepositoryProxy();
        Object proxy = Proxy.newProxyInstance(repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface}, handler);
        for (Method m : repositoryInterface.getMethods()) {
            if (m.getDeclaringClass() == Object.class) {
                continue;
            }
            if (m.isDefault()) {
                handler.dispatch.put(m, args -> InvocationHandler.invokeDefault(proxy, m, args));
            } else if (m.getDeclaringClass().getName().startsWith("org.springframework.data.repository")) {
                Method target = delegateMethodOrThrow(delegate.getClass(), m);
                handler.dispatch.put(m, args -> invokeUnchecked(target, delegate, args));
            } else {
                handler.dispatch.put(m, invokerFor(m, entity, operations));
            }
        }
        return proxy;
    }

    /**
     * Resolves the executor for a user-declared abstract method via the derived-query
     * resolver (bootstrap-time grammar, bridging, role pre-checks).
     *
     * @param m          user method
     * @param entity     bound domain metamodel
     * @param operations template for query execution
     * @return the method invoker
     * @throws io.github.lamspace.meili.repository.exception.MeiliRepositoryConfigurationException
     *         for grammar/shape/arity failures
     * @throws io.github.lamspace.meili.core.exception.MeiliMappingException
     *         for bridge and role-precheck failures
     */
    private static MethodInvoker invokerFor(Method m, MeiliPersistentEntity entity,
                                            MeiliSearchOperations operations) {
        if (m.isAnnotationPresent(io.github.lamspace.meili.repository.MeiliQuery.class)) {
            return MeiliAnnotatedQueries.bootstrap(m, entity, operations)::invoke;
        }
        return MeiliDerivedQueries.bootstrap(m, entity, operations)::invoke;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> "MeiliRepositoryProxy$" + Integer.toHexString(System.identityHashCode(proxy));
            };
        }
        MethodInvoker invoker = dispatch.get(method);
        if (invoker == null) {
            throw new UnsupportedOperationException("未注册的方法: " + method);
        }
        try {
            return invoker.invoke(args == null ? new Object[0] : args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Locates the delegate's public implementation for an inherited API method.
     *
     * @param clazz       delegate class
     * @param ifaceMethod repository interface method
     * @return matching public method on the delegate
     */
    private static Method delegateMethodOrThrow(Class<?> clazz, Method ifaceMethod) {
        try {
            return clazz.getMethod(ifaceMethod.getName(), ifaceMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new MeiliMappingException("默认仓库实现缺少接口方法: " + ifaceMethod, e);
        }
    }

    /**
     * Reflectively invokes a delegate method, unwrapping application failures so callers
     * observe the same exception types as with the template directly.
     *
     * @param target         delegate method
     * @param targetInstance delegate instance
     * @param args           runtime arguments
     * @return invocation result
     */
    private static Object invokeUnchecked(Method target, Object targetInstance, Object[] args) {
        try {
            return target.invoke(targetInstance, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new MeiliMappingException("仓库委托失败: " + target, cause);
        } catch (IllegalAccessException e) {
            throw new MeiliMappingException("仓库委托不可访问: " + target, e);
        }
    }
}
