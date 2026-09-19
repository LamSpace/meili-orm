package io.github.lamspace.meili.core.event;

/**
 * Marker for every entity lifecycle callback flavor, so a collecting container can gather
 * all callback beans by this one type and hand them to
 * {@link MeiliEntityCallbacks#register(MeiliCallback)} uniformly.
 */
public interface MeiliCallback {
}
