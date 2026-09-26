package io.github.lamspace.meili.repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a hand-written MeiliSearch query on a repository method — the controlled escape
 * hatch beyond derived-query grammar (explicit {@code distinct}, native filter DSL).
 *
 * <p><b>Template syntax.</b> {@link #filter()} and {@link #q()} support three placeholder
 * kinds: positional {@code ?0}…{@code ?n}, named {@code :paramName} (bound via
 * {@code @Param} or compiled parameter names) and raw SpEL {@code #{...}} referencing those
 * variables as {@code #name}/{@code #argN}. In a filter template, substituted String values are automatically wrapped
 * as escaped Meili string literals (the template never carries the quotes); numbers/booleans
 * render bare. In a {@code q} template, values substitute verbatim as full-text — no DSL
 * escaping applies there.
 *
 * <p><b>Precedence.</b> The annotation owns q/filter/distinct; the method name still
 * contributes OrderBy and Top semantics; remaining {@code By…} criteria are ignored with a
 * startup WARN. Validation of placeholders and filter bracket pairing happens at bootstrap;
 * server-side DSL syntax errors propagate as access exceptions at call time.
 *
 * <p>Note: this annotation shares its simple name with the core query-builder class
 * {@code io.github.lamspace.meili.core.query.MeiliQuery} by design (spec-named); repository
 * interfaces normally import only the annotation, and the proxy layer fully-qualifies both.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MeiliQuery {

    /**
     * Full-text query template.
     *
     * @return q template; empty means no text query
     */
    String q() default "";

    /**
     * Filter DSL template (no quotes around placeholders).
     *
     * @return filter template; empty means no filter
     */
    String filter() default "";

    /**
     * Attribute for Meili result de-duplication.
     *
     * @return distinct attribute path; empty disables deduplication
     */
    String distinct() default "";
}
