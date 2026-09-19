package io.github.lamspace.meili.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link MeiliSetting} declarations. Never annotated directly;
 * read via {@code getAnnotationsByType(MeiliSetting.class)} which flattens both forms.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeiliSettings {

    /**
     * The repeated settings declarations, in source order.
     *
     * @return all {@link MeiliSetting} annotations present on the entity
     */
    MeiliSetting[] value();
}
