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
package io.github.lamspace.meili.core.internal;

import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Write-path timestamp audit filler for {@code @CreatedDate}/{@code @LastModifiedDate}
 * (internal; invoked by the operations facade before {@code BeforeConvertCallback}).
 *
 * <p>Filling semantics per save call, all fields anchored to one supplied instant:
 * a created-date field keeps its current value when non-empty ({@code null} for object
 * types; only the primitive {@code long} treats {@code 0} as "unset") and is set to the
 * anchor otherwise; a last-modified-date field is overwritten unconditionally. The
 * converted value follows the declared field type: {@code Instant} verbatim,
 * {@code OffsetDateTime}/{@code ZonedDateTime}/{@code LocalDateTime} via the system
 * default zone, {@code long}/{@code Long} as epoch milliseconds.
 *
 * <p>Entity shapes: a POJO is mutated in place through its (already accessible) declared
 * fields and returned as the same instance; a record is rebuilt through its canonical
 * constructor — every non-audit component value is carried over verbatim via the
 * component accessor — so the caller must use the returned instance, which differs from
 * the input. Compact-constructor side effects re-execute on rebuild; entities are
 * required to keep those constructors idempotent (documented contract).
 *
 * <p>Fast path: {@link MeiliPersistentEntity#hasAuditFields()} {@code == false} returns
 * the input instance untouched, with zero reflection — entities without audit
 * declarations are never slowed down.
 *
 * <p>Stateless and thread-safe; every call operates only on its arguments. Reflection
 * failures surface as {@link MeiliMappingException} naming the entity.
 */
public final class MeiliAuditSupport {

    /**
     * Never called — static entry point only.
     */
    private MeiliAuditSupport() {
    }

    /**
     * Fills the audit fields of one entity at the given write instant.
     *
     * @param entity the entity to audit; non-{@code null}
     * @param meta   metamodel of the entity's class (supplies the audit field lists)
     * @param now    the write instant all filled values are derived from
     * @param <T>    entity type
     * @return the entity to serialize onward: the same instance for POJOs and for
     *         audit-free entities, a rebuilt instance for records
     * @throws MeiliMappingException when a field read/write or record rebuild fails
     */
    public static <T> T audit(T entity, MeiliPersistentEntity meta, Instant now) {
        if (!meta.hasAuditFields()) {
            return entity;
        }
        Class<?> type = meta.getType();
        if (type.isRecord()) {
            Map<String, Object> replacements = new LinkedHashMap<>();
            for (Field f : meta.getCreatedDateFields()) {
                if (isEmpty(readField(f, entity, type), f.getType())) {
                    replacements.put(f.getName(), convert(f.getType(), now));
                }
            }
            for (Field f : meta.getLastModifiedDateFields()) {
                replacements.put(f.getName(), convert(f.getType(), now));
            }
            return rebuildRecord(entity, type, replacements);
        }
        for (Field f : meta.getCreatedDateFields()) {
            if (isEmpty(readField(f, entity, type), f.getType())) {
                writeField(f, entity, type, convert(f.getType(), now));
            }
        }
        for (Field f : meta.getLastModifiedDateFields()) {
            writeField(f, entity, type, convert(f.getType(), now));
        }
        return entity;
    }

    /**
     * Decides "unset" for a created-date fill: object types only check for {@code null};
     * the primitive {@code long} additionally treats {@code 0} as its sentinel value.
     *
     * @param value     current boxed value (never {@code null} for primitives)
     * @param fieldType declared field type
     * @return whether the created-date field is eligible for filling
     */
    private static boolean isEmpty(Object value, Class<?> fieldType) {
        if (value == null) {
            return true;
        }
        return fieldType == long.class && (Long) value == 0L;
    }

    /**
     * Derives the value of one field type from the single write instant.
     *
     * @param fieldType declared audit type (validated at parse time)
     * @param now       write instant
     * @return the value to store, matching {@code fieldType}
     */
    private static Object convert(Class<?> fieldType, Instant now) {
        if (fieldType == Instant.class) {
            return now;
        }
        if (fieldType == OffsetDateTime.class) {
            return OffsetDateTime.ofInstant(now, ZoneId.systemDefault());
        }
        if (fieldType == ZonedDateTime.class) {
            return ZonedDateTime.ofInstant(now, ZoneId.systemDefault());
        }
        if (fieldType == LocalDateTime.class) {
            return LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        }
        return now.toEpochMilli(); // long / Long
    }

    /**
     * Reads one declared field via the accessible handle prepared at parse time.
     *
     * @param f      audit field (already {@code setAccessible})
     * @param entity instance to read
     * @param type   entity class for failure messages
     * @return the boxed current value
     */
    private static Object readField(Field f, Object entity, Class<?> type) {
        try {
            return f.get(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new MeiliMappingException("failed to read audit field: " + type.getName() + "." + f.getName(),
                    unwrap(e));
        }
    }

    /**
     * Writes one POJO audit field via the accessible handle prepared at parse time.
     *
     * @param f      audit field (already {@code setAccessible})
     * @param entity instance to mutate
     * @param type   entity class for failure messages
     * @param value  value to store
     */
    private static void writeField(Field f, Object entity, Class<?> type, Object value) {
        try {
            f.set(entity, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new MeiliMappingException("failed to write audit field: "
                    + type.getName() + "." + f.getName(), unwrap(e));
        }
    }

    /**
     * Rebuilds a record instance through its canonical constructor, substituting the
     * audit components with their filled values and carrying every other component over
     * unchanged via its accessor.
     *
     * @param <T>          entity type
     * @param entity       original instance
     * @param type         record class
     * @param replacements component name to new value, in fill order
     * @return the rebuilt record instance
     */
    @SuppressWarnings("unchecked")
    private static <T> T rebuildRecord(T entity, Class<?> type, Map<String, Object> replacements) {
        try {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] paramTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                paramTypes[i] = components[i].getType();
                String name = components[i].getName();
                if (replacements.containsKey(name)) {
                    args[i] = replacements.get(name);
                } else {
                    Method accessor = components[i].getAccessor();
                    // public accessor on a package-private record stays inaccessible otherwise
                    accessor.setAccessible(true);
                    args[i] = accessor.invoke(entity);
                }
            }
            Constructor<T> canonical = (Constructor<T>) type.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            return canonical.newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new MeiliMappingException("failed to rebuild record with audit values: "
                    + type.getName(), unwrap(e));
        }
    }

    /**
     * Unwraps the reflection-layer exception so the user-visible cause of a failed
     * accessor or constructor call (e.g. a throwing compact constructor) is preserved.
     *
     * @param e the caught exception
     * @return the innermost meaningful cause, or {@code e} itself
     */
    private static Throwable unwrap(Exception e) {
        return e instanceof InvocationTargetException ite && ite.getCause() != null
                ? ite.getCause() : e;
    }
}
