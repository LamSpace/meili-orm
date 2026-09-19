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
