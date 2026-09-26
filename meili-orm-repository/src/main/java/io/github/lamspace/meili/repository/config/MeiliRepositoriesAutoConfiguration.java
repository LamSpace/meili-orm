package io.github.lamspace.meili.repository.config;

import io.github.lamspace.meili.autoconfigure.MeiliDataAutoConfiguration;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.repository.MeiliRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Fallback registration of {@code MeiliRepository} interfaces for Boot applications that add
 * this module without writing {@code @EnableMeiliRepositories}.
 *
 * <p><b>Condition chain (all must hold).</b>
 * <ol>
 *   <li>{@link MeiliRepository} on the classpath — this class ships in the same artifact, so
 *       the condition is really about partial-classpath resilience;</li>
 *   <li>the data auto-configuration ran first: {@link MeiliSearchOperations} and
 *       {@link MeiliMappingContext} beans must exist, otherwise repositories would be dead
 *       wiring; the whole configuration backs off silently;</li>
 *   <li>{@code meili.repositories.enabled} absent or {@code true};</li>
 *   <li>no {@code @EnableMeiliRepositories} marker present — an explicit enable wins
 *       (the marker-based back-off mirrors Spring Boot's own repositories auto-configures).</li>
 * </ol>
 *
 * <p><b>Package source.</b> Without the annotation, the scan uses
 * {@code @AutoConfigurationPackages} (the Boot application package); contexts lacking that
 * marker (non-Boot usage) skip registration with a debug log rather than scanning
 * indiscriminately.
 */
@AutoConfiguration(after = MeiliDataAutoConfiguration.class)
@ConditionalOnClass(MeiliRepository.class)
@ConditionalOnBean({MeiliSearchOperations.class, MeiliMappingContext.class})
@ConditionalOnProperty(prefix = "meili.repositories", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnMissingBean(MeiliRepositoriesRegistrar.EnabledConfiguration.class)
@Import(MeiliDefaultRepositoriesRegistrar.class)
public class MeiliRepositoriesAutoConfiguration {

    /** Public no-arg constructor required by Boot's configuration-class instantiation. */
    public MeiliRepositoriesAutoConfiguration() {
    }
}
