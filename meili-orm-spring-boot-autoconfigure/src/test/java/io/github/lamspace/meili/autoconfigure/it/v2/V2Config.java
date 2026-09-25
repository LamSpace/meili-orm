package io.github.lamspace.meili.autoconfigure.it.v2;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Boot-shaped configuration rooted at the v2 package (the drifted entity set).
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@AutoConfigurationPackage
public class V2Config {
}
