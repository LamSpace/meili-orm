package io.github.lamspace.meili.autoconfigure.it.v1;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Boot-shaped configuration rooted at the v1 package: the entity scan of a context using
 * this class discovers only the v1 {@code ITBook}.
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@AutoConfigurationPackage
public class V1Config {
}
