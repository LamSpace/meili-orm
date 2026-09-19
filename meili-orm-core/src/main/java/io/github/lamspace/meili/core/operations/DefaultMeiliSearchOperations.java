package io.github.lamspace.meili.core.operations;

import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import io.github.lamspace.meili.core.task.MeiliTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Default {@link MeiliSearchOperations}: every call resolves the entity metamodel through
 * the shared {@link MeiliMappingContext}, crosses the JSON boundary exclusively via the
 * pluggable {@link MeiliDocumentSerializer}, and reaches the server only through the
 * internal {@link MeiliRawGateway} — which is why no transport type appears anywhere on
 * this surface.
 *
 * <p>Callback choreography (fixed ordering contract):
 * <ul>
 *   <li>write: BeforeConvert → serialize → send → (await when wait-task) → AfterSave;</li>
 *   <li>read: fetch raw → AfterLoad (raw JSON still editable) → deserialize →
 *       AfterConvert.</li>
 * </ul>
 * A callback may return a replacement instance; chains pass each result onward.
 *
 * <p>State: injected collaborators plus immutable configuration only — thread-safe under
 * concurrent use, provided the callbacks registry follows its own startup-registration
 * contract. The wait-task flag and wait budget are fixed per instance (wiring-time
 * configuration).
 */
public final class DefaultMeiliSearchOperations implements MeiliSearchOperations {

    /** Transport gateway (internal SPI). */
    private final MeiliRawGateway gateway;
    /** Shared metamodel cache. */
    private final MeiliMappingContext context;
    /** JSON boundary. */
    private final MeiliDocumentSerializer serializer;
    /** Lifecycle callback registry. */
    private final MeiliEntityCallbacks callbacks;
    /** Whether writes block until their task is terminal. */
    private final boolean waitTask;
    /** Wait budget for blocking awaits. */
    private final Duration waitTimeout;

    /**
     * Wires one operations facade.
     *
     * @param gateway     transport gateway
     * @param context     metamodel cache
     * @param serializer  document serializer
     * @param callbacks   lifecycle callbacks (never {@code null}; use
     *                    {@link MeiliEntityCallbacks#none()} for none)
     * @param waitTask    block writes until their task reaches a terminal state
     * @param waitTimeout budget for every await made by this instance
     */
    public DefaultMeiliSearchOperations(MeiliRawGateway gateway, MeiliMappingContext context,
                                        MeiliDocumentSerializer serializer,
                                        MeiliEntityCallbacks callbacks,
                                        boolean waitTask, Duration waitTimeout) {
        this.gateway = gateway;
        this.context = context;
        this.serializer = serializer;
        this.callbacks = callbacks;
        this.waitTask = waitTask;
        this.waitTimeout = waitTimeout;
    }

    @Override
    public <T> T save(T entity) {
        if (entity == null) {
            throw new MeiliOrmException("save 需要实体实例");
        }
        MeiliPersistentEntity meta = context.getEntity(entity.getClass());
        String index = meta.getIndexName();
        T converted = callbacks.onBeforeConvert(entity, index);
        requireId(meta, converted);
        int taskUid = gateway.updateDocuments(index, serializer.write(converted));
        if (waitTask) {
            gateway.awaitTask(taskUid, waitTimeout);
        }
        return callbacks.onAfterSave(converted, index);
    }

    @Override
    public <T> List<T> saveAll(Iterable<T> entities) {
        List<T> staged = new ArrayList<>();
        MeiliPersistentEntity meta = null;
        String index = null;
        for (T entity : entities) {
            if (entity == null) {
                throw new MeiliOrmException("saveAll 不接受 null 实体");
            }
            MeiliPersistentEntity current = context.getEntity(entity.getClass());
            if (meta == null) {
                meta = current;
                index = meta.getIndexName();
            } else if (meta != current) {
                throw new MeiliOrmException("saveAll 仅支持同一实体类型: "
                        + meta.getType().getName() + " vs " + current.getType().getName());
            }
            T converted = callbacks.onBeforeConvert(entity, index);
            requireId(meta, converted);
            staged.add(converted);
        }
        if (staged.isEmpty()) {
            return List.of();
        }
        StringJoiner array = new StringJoiner(",", "[", "]");
        for (T entity : staged) {
            array.add(serializer.write(entity));
        }
        int taskUid = gateway.updateDocuments(index, array.toString());
        if (waitTask) {
            gateway.awaitTask(taskUid, waitTimeout);
        }
        List<T> saved = new ArrayList<>(staged.size());
        for (T entity : staged) {
            saved.add(callbacks.onAfterSave(entity, index));
        }
        return saved;
    }

    @Override
    public <T> Optional<T> findById(Object id, Class<T> type) {
        String key = requireKey(id);
        MeiliPersistentEntity meta = context.getEntity(type);
        String index = meta.getIndexName();
        return gateway.fetchRawDocument(index, key)
                .map(raw -> readDocument(raw, type, index));
    }

    @Override
    public <T> List<T> findAll(Class<T> type, DocumentsFetchQuery query) {
        MeiliPersistentEntity meta = context.getEntity(type);
        String index = meta.getIndexName();
        List<T> out = new ArrayList<>();
        for (String raw : gateway.fetchRawDocuments(index, query)) {
            out.add(readDocument(raw, type, index));
        }
        return out;
    }

    @Override
    public <T> void deleteById(Object id, Class<T> type) {
        String key = requireKey(id);
        String index = context.getEntity(type).getIndexName();
        int taskUid = gateway.deleteDocument(index, key);
        if (waitTask) {
            gateway.awaitTask(taskUid, waitTimeout);
        }
    }

    @Override
    public <T> void deleteAll(Class<T> type) {
        String index = context.getEntity(type).getIndexName();
        int taskUid = gateway.deleteAllDocuments(index);
        if (waitTask) {
            gateway.awaitTask(taskUid, waitTimeout);
        }
    }

    @Override
    public <T> long count(Class<T> type) {
        return gateway.count(context.getEntity(type).getIndexName());
    }

    @Override
    public void awaitTask(int taskUid) {
        gateway.awaitTask(taskUid, waitTimeout);
    }

    @Override
    public MeiliTask getTask(int taskUid) {
        return gateway.getTask(taskUid);
    }

    /**
     * Runs the read-side chain for one raw document.
     *
     * @param rawJson verbatim document JSON
     * @param type    target entity class
     * @param index   source index uid
     * @param <T>     entity type
     * @return the materialized entity
     */
    private <T> T readDocument(String rawJson, Class<T> type, String index) {
        String loaded = callbacks.onAfterLoad(rawJson, type, index);
        T entity = serializer.read(loaded, type);
        return callbacks.onAfterConvert(entity, index);
    }

    /**
     * Rejects entities whose primary-key value is null before any transport contact.
     *
     * @param meta    entity metamodel
     * @param entity  entity instance to inspect
     * @param <T>     entity type
     */
    private static <T> void requireId(MeiliPersistentEntity meta, T entity) {
        if (meta.idValue(entity) == null) {
            throw new MeiliOrmException("实体缺少主键值（@MeiliId 为 null）: "
                    + meta.getType().getName());
        }
    }

    /**
     * Normalizes a lookup key: non-null, rendered via its string form.
     *
     * @param id primary-key value
     * @return the key as string
     */
    private static String requireKey(Object id) {
        if (id == null) {
            throw new MeiliOrmException("查询主键不可为 null");
        }
        return String.valueOf(id);
    }
}
