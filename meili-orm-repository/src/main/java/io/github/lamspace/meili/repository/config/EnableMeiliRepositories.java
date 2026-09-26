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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables scanning and registration of {@code MeiliRepository} sub-interfaces.
 *
 * <p><b>Contract.</b> Exactly one trigger is active in a context: either this annotation or
 * the repository auto-configuration (which backs off once any {@code @Enable} has declared
 * the capability). Registration creates one singleton proxy per discovered interface, wired
 * to the context's {@code MeiliSearchOperations}.
 *
 * <p><b>Package selection.</b> {@link #basePackages()} (alias {@link #value()}) wins when
 * non-empty; {@link #basePackageClasses()} contributes each referenced class's package;
 * with neither, the annotated configuration class's own package is scanned — the same
 * default Spring Data repositories use.
 *
 * <p><b>Discovery rules.</b> An interface is registered when it (recursively) extends
 * {@code MeiliRepository}, is not {@code org.springframework.data.repository.NoRepositoryBean}
 * annotated, and is independent (not a non-static inner interface). Interfaces with
 * unresolvable query methods fail context startup (bootstrap-time validation), never
 * first-call.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MeiliRepositoriesRegistrar.class)
public @interface EnableMeiliRepositories {

    /**
     * Alias for {@link #basePackages()}.
     *
     * @return packages to scan
     */
    String[] value() default {};

    /**
     * Base packages to scan for repository interfaces.
     *
     * @return configured packages, empty by default (annotated class's package applies)
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()}: each class contributes its own package.
     *
     * @return marker classes, empty by default
     */
    Class<?>[] basePackageClasses() default {};
}
