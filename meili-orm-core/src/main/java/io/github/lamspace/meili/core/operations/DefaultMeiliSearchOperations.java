package io.github.lamspace.meili.core.operations;

import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.exception.MeiliOrmException;
import io.github.lamspace.meili.core.internal.MeiliAuditSupport;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import io.github.lamspace.meili.core.settings.MeiliSettingsProjection;
import io.github.lamspace.meili.core.settings.ProjectedSettings;
import io.github.lamspace.meili.core.task.MeiliTask;
import java.time.Duration;
import java.time.Instant;
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
 *   <li>write: audit fill (when the entity declares audit fields) → BeforeConvert →
 *       serialize → send → (await when wait-task) → AfterSave;</li>
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
    /** Stateless projector used by the index-lifecycle segment. */
    private final MeiliSettingsProjection projection = new MeiliSettingsProjection();

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
        T audited = MeiliAuditSupport.audit(entity, meta, Instant.now());
        T converted = callbacks.onBeforeConvert(audited, index);
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
            T audited = MeiliAuditSupport.audit(entity, current, Instant.now());
            T converted = callbacks.onBeforeConvert(audited, index);
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

    @Override
    public <T> MeiliSearchResult<T> search(String q, Class<T> type) {
        return search(MeiliQuery.query(q), type);
    }

    @Override
    public <T> MeiliSearchResult<T> search(MeiliQuery query, Class<T> type) {
        MeiliPersistentEntity meta = context.getEntity(type);
        String index = meta.getIndexName();
        String raw = gateway.rawSearch(index, query);
        // hits ride the same read chain as document fetches: wrap the serializer so the
        // envelope parser needs no callback knowledge of its own
        return MeiliSearchResult.from(raw, type, readChainSerializer(index));
    }

    @Override
    public <T> List<MeiliSearchResult<T>> multiSearch(List<MeiliQuery> queries, Class<T> type) {
        List<MeiliSearchResult<T>> out = new ArrayList<>(queries.size());
        for (MeiliQuery query : queries) {
            out.add(search(query, type));
        }
        return out;
    }

    @Override
    public <T> boolean indexExists(Class<T> type) {
        return gateway.indexExists(context.getEntity(type).getIndexName());
    }

    @Override
    public <T> int createIndex(Class<T> type) {
        MeiliPersistentEntity meta = context.getEntity(type);
        int taskUid = gateway.createIndex(meta.getIndexName(), meta.getIdProperty().getJsonPath());
        ProjectedSettings settings = projection.project(meta);
        if (settings.hasAny()) {
            taskUid = gateway.updateSettings(meta.getIndexName(), settings.toJson());
        }
        return taskUid;
    }

    @Override
    public <T> void deleteIndex(Class<T> type) {
        int taskUid = gateway.deleteIndex(context.getEntity(type).getIndexName());
        if (waitTask) {
            gateway.awaitTask(taskUid, waitTimeout);
        }
    }

    @Override
    public <T> int applySettings(Class<T> type) {
        MeiliPersistentEntity meta = context.getEntity(type);
        ProjectedSettings settings = projection.project(meta);
        if (!settings.hasAny()) {
            throw new MeiliOrmException("实体未声明任何 settings 投影，无需推送: "
                    + meta.getType().getName());
        }
        return gateway.updateSettings(meta.getIndexName(), settings.toJson());
    }

    @Override
    public <T> ProjectedSettings projectedSettings(Class<T> type) {
        return projection.project(context.getEntity(type));
    }

    /**
     * Adapts the configured serializer so each {@code read} is wrapped by the read-side
     * callback chain for one index — the exact chain {@link #readDocument} applies.
     *
     * @param index source index uid
     * @return a delegating serializer view, not shared beyond one call
     */
    private MeiliDocumentSerializer readChainSerializer(String index) {
        return new MeiliDocumentSerializer() {
            @Override
            public String write(Object document) {
                return serializer.write(document);
            }

            @Override
            public <T> T read(String json, Class<T> target) {
                String loaded = callbacks.onAfterLoad(json, target, index);
                T entity = serializer.read(loaded, target);
                return callbacks.onAfterConvert(entity, index);
            }
        };
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
