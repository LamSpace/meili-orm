package io.github.lamspace.meili.core.event;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight registry for the four entity lifecycle callback flavors: stores each
 * registration together with the concrete entity type resolved from its generic argument,
 * and fires only the callbacks assignable from the entity (or entity class for
 * {@link #onAfterLoad}) in registration order.
 *
 * <p>Resolution rules at {@link #register(MeiliCallback)} time: the callback class (and
 * its superclass chain) must name exactly one of the four flavors; its type argument must
 * be a concrete class. A raw implementation, a type-variable argument, or a callback
 * declaring none of the flavors fails immediately with {@link MeiliMappingException} —
 * an unusable callback never sits silently in the chain. One class may declare several
 * flavors and is then bound once per flavor. Lambdas carry no generic type argument at
 * runtime (JVM erasure of hidden classes), so they register through the explicit
 * {@link #register(Class, MeiliCallback)} overload instead.
 *
 * <p>Thread model: registration is expected during startup/wiring; trigger methods are
 * safe for concurrent use because each flavor list is copy-on-write (iteration reflects a
 * stable snapshot; a registration racing a trigger is picked up by later triggers only).
 * Trigger chaining passes each result into the next matching callback.
 */
public final class MeiliEntityCallbacks {

    /** Which lifecycle slot a registered callback occupies. */
    private enum Kind {
        /** Pre-serialization hook on write. */
        BEFORE_CONVERT,
        /** Post-write hook. */
        AFTER_SAVE,
        /** Pre-deserialization raw-document hook on read. */
        AFTER_LOAD,
        /** Post-deserialization hook on read. */
        AFTER_CONVERT,
    }

    /**
     * One resolved registration.
     *
     * @param targetType concrete entity class the callback declared
     * @param callback   the callback instance
     */
    private record Binding(Class<?> targetType, MeiliCallback callback) {
    }

    /** Registrations per flavor, in insertion order. */
    private final Map<Kind, List<Binding>> bindings;
    /** Total registrations, for introspection by auto-configuration. */
    private int registeredCount;

    /** Creates an empty registry. */
    public MeiliEntityCallbacks() {
        bindings = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            bindings.put(kind, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Creates an empty pass-through registry for call sites without configured callbacks.
     *
     * @return a fresh registry with zero bindings
     */
    public static MeiliEntityCallbacks none() {
        return new MeiliEntityCallbacks();
    }

    /**
     * Registers one callback, resolving its flavor(s) and target entity type.
     *
     * @param callback implementation declaring at least one flavor with a concrete type
     *                 argument
     * @throws MeiliMappingException when the flavor cannot be determined or the generic
     *         argument is not a concrete class
     */
    public void register(MeiliCallback callback) {
        int added = 0;
        for (Class<?> c = callback.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Type t : c.getGenericInterfaces()) {
                Kind kind;
                Class<?> target;
                if (t instanceof ParameterizedType pt) {
                    kind = kindOf(pt.getRawType());
                    if (kind == null) {
                        continue;
                    }
                    if (!(pt.getActualTypeArguments()[0] instanceof Class<?> concrete)) {
                        throw new MeiliMappingException("回调泛型实参不可解析: "
                                + callback.getClass().getName());
                    }
                    target = concrete;
                } else if (t instanceof Class<?> raw && kindOf(raw) != null) {
                    throw new MeiliMappingException("回调未提供具体泛型实参: "
                            + callback.getClass().getName());
                } else {
                    continue;
                }
                bindings.get(kind).add(new Binding(target, callback));
                added++;
            }
        }
        if (added == 0) {
            throw new MeiliMappingException("回调未声明任何生命周期接口: " + callback.getClass().getName());
        }
        registeredCount += added;
    }

    /**
     * Registers one callback with an explicitly supplied target entity type — required
     * for lambda instances, whose generic argument is not retained by the JVM.
     *
     * @param entityType concrete entity class the callback applies to
     * @param callback   implementation of at least one flavor
     * @throws MeiliMappingException when the callback implements none of the four flavors
     *         or {@code entityType} is {@code null}
     */
    public void register(Class<?> entityType, MeiliCallback callback) {
        if (entityType == null) {
            throw new MeiliMappingException("显式注册回调需要目标实体类型");
        }
        int added = 0;
        for (Kind kind : Kind.values()) {
            if (flavorInstance(kind, callback)) {
                bindings.get(kind).add(new Binding(entityType, callback));
                added++;
            }
        }
        if (added == 0) {
            throw new MeiliMappingException("回调未声明任何生命周期接口: " + callback.getClass().getName());
        }
        registeredCount += added;
    }

    /**
     * Tests a callback instance against one flavor via runtime interface check.
     *
     * @param kind     flavor slot
     * @param callback candidate instance
     * @return whether the instance implements that flavor
     */
    private static boolean flavorInstance(Kind kind, MeiliCallback callback) {
        return switch (kind) {
            case BEFORE_CONVERT -> callback instanceof BeforeConvertCallback;
            case AFTER_SAVE -> callback instanceof AfterSaveCallback;
            case AFTER_LOAD -> callback instanceof AfterLoadCallback;
            case AFTER_CONVERT -> callback instanceof AfterConvertCallback;
        };
    }

    /**
     * Maps a raw interface to its lifecycle slot.
     *
     * @param rawType the declared interface class
     * @return the slot, or {@code null} when not a callback flavor
     */
    private static Kind kindOf(Type rawType) {
        if (rawType == BeforeConvertCallback.class) {
            return Kind.BEFORE_CONVERT;
        }
        if (rawType == AfterSaveCallback.class) {
            return Kind.AFTER_SAVE;
        }
        if (rawType == AfterLoadCallback.class) {
            return Kind.AFTER_LOAD;
        }
        if (rawType == AfterConvertCallback.class) {
            return Kind.AFTER_CONVERT;
        }
        return null;
    }

    /**
     * Fires the write-side pre-conversion chain.
     *
     * @param entity    entity about to be serialized; not {@code null}
     * @param indexName target index uid
     * @param <T>       entity type
     * @return the entity after all matching callbacks ran
     */
    @SuppressWarnings("unchecked")
    public <T> T onBeforeConvert(T entity, String indexName) {
        T current = entity;
        for (Binding b : bindings.get(Kind.BEFORE_CONVERT)) {
            if (b.targetType().isAssignableFrom(current.getClass())) {
                current = ((BeforeConvertCallback<T>) b.callback()).onBeforeConvert(current, indexName);
            }
        }
        return current;
    }

    /**
     * Fires the write-side post-save chain.
     *
     * @param entity    the saved entity; not {@code null}
     * @param indexName target index uid
     * @param <T>       entity type
     * @return the entity after all matching callbacks ran
     */
    @SuppressWarnings("unchecked")
    public <T> T onAfterSave(T entity, String indexName) {
        T current = entity;
        for (Binding b : bindings.get(Kind.AFTER_SAVE)) {
            if (b.targetType().isAssignableFrom(current.getClass())) {
                current = ((AfterSaveCallback<T>) b.callback()).onAfterSave(current, indexName);
            }
        }
        return current;
    }

    /**
     * Chains the read-side raw-document rewrites.
     *
     * @param rawDocumentJson verbatim fetched document; not {@code null}
     * @param entityType      class the document will deserialize into
     * @param indexName       source index uid
     * @return the document JSON after all matching callbacks ran
     */
    public String onAfterLoad(String rawDocumentJson, Class<?> entityType, String indexName) {
        String current = rawDocumentJson;
        for (Binding b : bindings.get(Kind.AFTER_LOAD)) {
            if (b.targetType().isAssignableFrom(entityType)) {
                current = ((AfterLoadCallback<?>) b.callback()).onAfterLoad(current, indexName);
            }
        }
        return current;
    }

    /**
     * Fires the read-side post-conversion chain.
     *
     * @param entity    the deserialized entity; not {@code null}
     * @param indexName source index uid
     * @param <T>       entity type
     * @return the entity after all matching callbacks ran
     */
    @SuppressWarnings("unchecked")
    public <T> T onAfterConvert(T entity, String indexName) {
        T current = entity;
        for (Binding b : bindings.get(Kind.AFTER_CONVERT)) {
            if (b.targetType().isAssignableFrom(current.getClass())) {
                current = ((AfterConvertCallback<T>) b.callback()).onAfterConvert(current, indexName);
            }
        }
        return current;
    }

    /**
     * Reports how many callback bindings are registered (a class declaring several
     * flavors counts once per flavor).
     *
     * @return total binding count
     */
    public int registeredCount() {
        return registeredCount;
    }
}
