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
package io.github.lamspace.meili.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a passthrough MeiliSearch settings resource for the annotated entity — the
 * single carrier for settings that cannot be expressed as field roles (ranking rules,
 * synonyms, stop words, faceting, pagination, typo tolerance, ...).
 *
 * <p>The referenced JSON file is merged into the projection produced from role annotations
 * with <em>passthrough precedence</em>: a key present in the file overrides the projected
 * key of the same name. Keys outside the known MeiliSearch settings vocabulary are
 * rejected at projection time.
 *
 * <p>Repeatable; declaration order is preserved and earlier files lose to later ones on
 * the same key. Deliberately kept off {@link MeiliDocument} so index identity and
 * settings transport stay independent contracts.
 */
@Documented
@Repeatable(MeiliSettings.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeiliSetting {

    /**
     * Location of the settings JSON resource; supports the {@code classpath:} prefix.
     *
     * @return the resource path, resolved against the current thread's context
     *         classloader (caller duty in container-less environments)
     */
    String settingPath();
}
