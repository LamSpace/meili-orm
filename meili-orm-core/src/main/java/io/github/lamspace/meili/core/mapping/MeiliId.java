package io.github.lamspace.meili.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the primary-key property of an entity. Exactly one property per entity must
 * carry it, and its type must be {@code String}, {@code Long}, {@code long},
 * {@code Integer} or {@code int} — the only shapes MeiliSearch accepts.
 *
 * <p>Explicit declaration also neutralizes the server's trailing-{@code id} auto-inference
 * (multiple inference candidates fail the write server-side): the projected name of this
 * property is the index's primary key whatever it is called.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeiliId {
}
