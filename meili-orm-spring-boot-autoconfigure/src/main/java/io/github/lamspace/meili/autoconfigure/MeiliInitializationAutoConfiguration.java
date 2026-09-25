package io.github.lamspace.meili.autoconfigure;

import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.settings.MeiliSettingsProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Index-initialization auto configuration: contributes the standalone
 * {@link MeiliIndexInitializer} on top of the data layer.
 *
 * <p>Kept as its own auto configuration (rather than a bean inside the data layer) so it can
 * be replaced or backed off independently — a user-supplied {@link MeiliIndexInitializer}
 * bean retires ours entirely. Activation follows the data layer: it needs a
 * {@link MeiliRawGateway} bean (hence a wired client) and {@code meili.enabled} on;
 * {@code auto-init=none} leaves the driver in place but inert (zero server interaction),
 * which keeps the conditional chain simple and the mode semantics authoritative in one place.
 *
 * <p>Entity sourcing: the scan base is the application's auto-configuration packages. Outside
 * a Boot application (no declared packages) the scan yields an empty list and initialization
 * is a no-op — with a debug log, not an error.
 */
@AutoConfiguration(after = MeiliDataAutoConfiguration.class)
@ConditionalOnBean(MeiliRawGateway.class)
@ConditionalOnProperty(prefix = "meili", name = "enabled", matchIfMissing = true)
public class MeiliInitializationAutoConfiguration {

    /** Logger for the missing-packages fallback. */
    private static final Logger log = LoggerFactory.getLogger(MeiliInitializationAutoConfiguration.class);

    /**
     * Instantiated by the auto-configuration machinery; all behavior lives in the bean methods.
     */
    public MeiliInitializationAutoConfiguration() {
    }

    /**
     * The settings projector used to derive per-entity settings documents.
     *
     * @return a stateless projector bean
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliSettingsProjection meiliSettingsProjection() {
        return new MeiliSettingsProjection();
    }

    /**
     * Contributes the startup index driver over the scanned entity set.
     *
     * @param gateway     the wired raw gateway (data layer precondition)
     * @param context     the entity metamodel cache
     * @param projection  the settings projector
     * @param properties  the bound {@code meili.*} values (mode, strategy, wait budget)
     * @param beanFactory bean factory used to resolve auto-configuration packages
     * @return the initializer, scheduled by the singleton lifecycle
     */
    @Bean
    @ConditionalOnMissingBean
    MeiliIndexInitializer meiliIndexInitializer(MeiliRawGateway gateway, MeiliMappingContext context,
                                                MeiliSettingsProjection projection,
                                                MeiliProperties properties, BeanFactory beanFactory) {
        List<Class<?>> entities = MeiliEntityScanner.scanPackages(resolvePackages(beanFactory));
        return new MeiliIndexInitializer(gateway, context, projection, entities,
                properties.getIndex().getAutoInit(), properties.getIndex().getOnSettingsDrift(),
                properties.getWaitTimeout());
    }

    /**
     * Resolves the scan bases, degrading to "none" outside a Boot application.
     *
     * @param beanFactory the owning bean factory
     * @return auto-configuration packages, or an empty list when not applicable
     */
    private static List<String> resolvePackages(BeanFactory beanFactory) {
        if (!AutoConfigurationPackages.has(beanFactory)) {
            log.debug("无 @AutoConfigurationPackage 声明，跳过 @MeiliDocument 扫描（索引初始化为空集）");
            return List.of();
        }
        return AutoConfigurationPackages.get(beanFactory);
    }
}
