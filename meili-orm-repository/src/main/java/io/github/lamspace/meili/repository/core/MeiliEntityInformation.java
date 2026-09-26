package io.github.lamspace.meili.repository.core;

import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import java.util.Objects;
import org.springframework.data.repository.core.EntityInformation;

/**
 * Adapter exposing a core {@link MeiliPersistentEntity} metamodel through the commons
 * {@link EntityInformation} contract (a two-generation zero-drift interface).
 *
 * <p><b>Single source of truth.</b> Identity facts are read from the already-validated core
 * metamodel, never re-parsed: the id value comes from the metamodel's cached accessor and
 * the id <em>type</em> is the declared type of the same {@code @MeiliId}-annotated member
 * (the type itself is not carried by the core metamodel surface, so it is looked up once at
 * construction from the annotated field and then frozen). This keeps the repository layer
 * and the core mapping layer definitionally consistent — there is no second annotation
 * model to drift.
 *
 * <p><b>Newness.</b> {@link #isNew(Object)} reports "new" exactly when the primary key is
 * {@code null}; MeiliSearch upserts do not generate ids, and the operations layer rejects
 * null-key writes, so null-id entities are a startup-time (not persistence-time) error.
 *
 * @param <T>  domain type
 * @param <ID> primary-key type
 */
public final class MeiliEntityInformation<T, ID> implements EntityInformation<T, ID> {

    /** Core metamodel supplying index/id-accessor facts. */
    private final MeiliPersistentEntity entity;
    /** Frozen declared type of the {@code @MeiliId} member. */
    private final Class<ID> idType;
    /** Domain class shortcut. */
    private final Class<T> javaType;

    /**
     * Wraps a parsed metamodel.
     *
     * @param entity the core metamodel of the domain type; required
     * @throws IllegalStateException    when the {@code @MeiliId} member cannot be located
     *                                  (impossible for a metamodel that finished validation)
     * @throws IllegalArgumentException when the metamodel is null
     */
    @SuppressWarnings("unchecked")
    public MeiliEntityInformation(MeiliPersistentEntity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.javaType = (Class<T>) entity.getType();
        this.idType = (Class<ID>) lookupIdType(entity.getType());
    }

    /**
     * Finds the declared type of the single {@code @MeiliId} member. The core metamodel has
     * already validated exactly-one + legal-type constraints at parse time, so this is a
     * type lookup against a guaranteed-present member — not a second validation pass.
     *
     * @param type entity class
     * @return declared primary-key type
     */
    private static Class<?> lookupIdType(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(io.github.lamspace.meili.core.mapping.MeiliId.class)) {
                    return f.getType();
                }
            }
        }
        throw new IllegalStateException("元模型校验通过后找不到 @MeiliId 成员: " + type.getName());
    }

    @Override
    public boolean isNew(T instance) {
        return instance != null && entity.idValue(instance) == null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ID getId(T instance) {
        return (ID) entity.idValue(instance);
    }

    @Override
    public Class<ID> getIdType() {
        return idType;
    }

    @Override
    public Class<T> getJavaType() {
        return javaType;
    }
}
