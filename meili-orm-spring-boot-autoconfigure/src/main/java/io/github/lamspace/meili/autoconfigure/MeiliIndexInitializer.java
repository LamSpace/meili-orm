package io.github.lamspace.meili.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.settings.MeiliSettingsProjection;
import io.github.lamspace.meili.core.settings.ProjectedSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Startup-time index lifecycle driver: creates missing indexes, pushes projected settings and
 * reconciles drift according to the configured {@link MeiliProperties.AutoInit} mode and
 * {@link MeiliProperties.Drift} strategy.
 *
 * <p>Thread model and timing: runs once via {@link SmartInitializingSingleton}, i.e. after
 * every singleton in the context has been instantiated, on the startup thread. Server calls
 * made here (existence checks, settings reads/writes, task awaits) are synchronous and block
 * startup until each task reaches a terminal state, bounded per wait by {@code waitTimeout}.
 *
 * <p>Fail-fast boundary (a contract, not an oversight): any server interaction failure —
 * including an unreachable service — propagates as a core index-access exception and fails
 * startup; drift under {@code FAIL} raises a mapping exception. Only {@code auto-init=none}
 * opts out entirely (zero requests, startup succeeds without a server). The modes differ on
 * existing indexes: {@code create-if-missing} diffs and reports per strategy but never writes
 * (an {@code apply} strategy is logged as suppressed; {@code fail} still fails);
 * {@code sync-settings} applies the full strategy including pushes.
 *
 * <p>Diffing compares only keys the projection declares — keys nobody declared are never
 * drift, never pushed. Among role arrays, {@code searchableAttributes} compares with order
 * (weights are semantic); the other arrays compare as sets.
 */
public final class MeiliIndexInitializer implements SmartInitializingSingleton {

    /** Logger for drift reports and lifecycle milestones. */
    private static final Logger log = LoggerFactory.getLogger(MeiliIndexInitializer.class);

    /** Envelope reader for diffing (settings values are small; never used for entities). */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Keys whose change makes the server reindex all documents. */
    private static final Set<String> REBUILD_KEYS = Set.of("filterableAttributes", "sortableAttributes");

    /** Raw-string gateway; the only channel to the server. */
    private final MeiliRawGateway gateway;

    /** Entity metamodel cache. */
    private final MeiliMappingContext context;

    /** Settings projector (role arrays + passthrough). */
    private final MeiliSettingsProjection projection;

    /** Scanned entity classes, in stable order. */
    private final List<Class<?>> entities;

    /** Initialization mode under which this driver runs. */
    private final MeiliProperties.AutoInit mode;

    /** Drift strategy under which this driver reports or applies. */
    private final MeiliProperties.Drift drift;

    /** Budget for every task wait performed here. */
    private final Duration waitTimeout;

    /**
     * Wires the driver to its collaborators.
     *
     * @param gateway     raw-string gateway (startup exceptions travel through it)
     * @param context     metamodel cache used to resolve index names and primary keys
     * @param projection  settings projector
     * @param entities    scanned entity classes; empty means the driver is a no-op
     * @param mode        initialization mode; {@code NONE} short-circuits everything
     * @param drift       strategy applied when declared-key drift is detected
     * @param waitTimeout budget for each awaited task
     */
    public MeiliIndexInitializer(MeiliRawGateway gateway, MeiliMappingContext context,
                                 MeiliSettingsProjection projection, List<Class<?>> entities,
                                 MeiliProperties.AutoInit mode, MeiliProperties.Drift drift,
                                 Duration waitTimeout) {
        this.gateway = gateway;
        this.context = context;
        this.projection = projection;
        this.entities = List.copyOf(entities);
        this.mode = mode;
        this.drift = drift;
        this.waitTimeout = waitTimeout;
    }

    /**
     * Hook into the singleton lifecycle; delegates to {@link #initialize()} so the logic is
     * directly unit-testable.
     */
    @Override
    public void afterSingletonsInstantiated() {
        initialize();
    }

    /**
     * Executes the startup index pass over the configured entities.
     *
     * @throws MeiliMappingException     on duplicate index names or drift under {@code FAIL}
     * @throws io.github.lamspace.meili.core.exception.MeiliIndexAccessException
     *         on any server failure, including unreachability (fail-fast, see class docs)
     */
    public void initialize() {
        if (mode == MeiliProperties.AutoInit.NONE || entities.isEmpty()) {
            return;
        }
        Map<String, Class<?>> byIndex = checkUniqueIndexNames();
        for (Map.Entry<String, Class<?>> entry : byIndex.entrySet()) {
            initializeOne(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Resolves metamodels and rejects duplicate index names before any server contact:
     * two entities sharing an index would silently fight over its settings.
     *
     * @return index name to entity class, in scan order
     * @throws MeiliMappingException when two entities claim the same index name
     */
    private Map<String, Class<?>> checkUniqueIndexNames() {
        Map<String, Class<?>> byIndex = new LinkedHashMap<>();
        for (Class<?> type : entities) {
            MeiliPersistentEntity meta = context.getEntity(type);
            Class<?> previous = byIndex.putIfAbsent(meta.getIndexName(), type);
            if (previous != null) {
                throw new MeiliMappingException("indexName \"" + meta.getIndexName()
                        + "\" 被多个实体声明: " + previous.getName() + " 与 " + type.getName());
            }
        }
        return byIndex;
    }

    /**
     * Runs the full pass for one index: create (with settings push) when missing, otherwise
     * diff-and-report per the mode/strategy contract.
     *
     * @param indexUid index name
     * @param type     claiming entity class
     */
    private void initializeOne(String indexUid, Class<?> type) {
        MeiliPersistentEntity meta = context.getEntity(type);
        ProjectedSettings projected = projection.project(meta);
        if (!gateway.indexExists(indexUid)) {
            int taskUid = gateway.createIndex(indexUid, meta.getIdProperty().getJsonPath());
            if (projected.hasAny()) {
                taskUid = gateway.updateSettings(indexUid, projected.toJson());
            }
            gateway.awaitTask(taskUid, waitTimeout);
            log.info("索引 {} 已创建并同步投影 settings（task {}）", indexUid, taskUid);
            return;
        }
        if (!projected.hasAny()) {
            log.debug("索引 {} 已存在且实体未声明 settings，零交互", indexUid);
            return;
        }
        List<String> drifted = diff(projected, gateway.getSettings(indexUid));
        if (drifted.isEmpty()) {
            log.debug("索引 {} settings 与投影一致", indexUid);
            return;
        }
        handleDrift(indexUid, projected, drifted);
    }

    /**
     * Computes drifted keys: every key declared by the projection whose current server value
     * differs (absent/null counts as differing for a declared key).
     *
     * @param projected       the projection to reconcile towards
     * @param currentSettings server settings JSON
     * @return drifted key names in projection order; empty means in sync
     */
    private List<String> diff(ProjectedSettings projected, String currentSettings) {
        JsonNode want;
        JsonNode have;
        try {
            want = MAPPER.readTree(projected.toJson());
            have = MAPPER.readTree(currentSettings);
        } catch (Exception e) {
            throw new MeiliMappingException("settings diff 解析失败", e);
        }
        List<String> drifted = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = want.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!matches(field.getKey(), field.getValue(), have.get(field.getKey()))) {
                drifted.add(field.getKey());
            }
        }
        return drifted;
    }

    /**
     * Decides whether one declared key is in sync with the server value.
     *
     * @param key  declared settings key
     * @param want projected value
     * @param have server value, possibly {@code null} (key absent)
     * @return whether no drift exists for this key
     */
    private static boolean matches(String key, JsonNode want, JsonNode have) {
        if (have == null || have.isNull()) {
            return false;
        }
        if (!want.isArray()) {
            return want.equals(have);
        }
        if ("searchableAttributes".equals(key)) {
            return want.equals(have);
        }
        return textSet(want).equals(textSet(have));
    }

    /**
     * Collects the text values of an array node for set comparison.
     *
     * @param array node expected to be an array of scalars
     * @return its values as strings
     */
    private static Set<String> textSet(JsonNode array) {
        Set<String> out = new LinkedHashSet<>();
        array.forEach(node -> out.add(node.asText()));
        return out;
    }

    /**
     * Applies the configured strategy to a detected drift.
     *
     * @param indexUid  index name
     * @param projected projection to push under {@code APPLY} in sync mode
     * @param drifted   drifted key names
     * @throws MeiliMappingException under the {@code FAIL} strategy (in either mode)
     */
    private void handleDrift(String indexUid, ProjectedSettings projected, List<String> drifted) {
        if (drift == MeiliProperties.Drift.FAIL) {
            throw new MeiliMappingException("settings 漂移且 on-settings-drift=fail: index="
                    + indexUid + " 漂移键=" + drifted);
        }
        if (mode == MeiliProperties.AutoInit.CREATE_IF_MISSING) {
            if (drift == MeiliProperties.Drift.APPLY) {
                log.warn("索引 {} 漂移键 {}：auto-init=create-if-missing 下 apply 被抑制，不推送 settings",
                        indexUid, drifted);
            } else {
                log.warn("索引 {} 漂移键 {}：auto-init=create-if-missing 只报告不写入", indexUid, drifted);
            }
            return;
        }
        if (drift == MeiliProperties.Drift.WARN) {
            log.warn("索引 {} settings 漂移，on-settings-drift=warn 不写入，漂移键 {}", indexUid, drifted);
            return;
        }
        if (drifted.stream().anyMatch(REBUILD_KEYS::contains)) {
            log.warn("索引 {} 的漂移涉及 {} 之一，服务端将全量重建该索引，任务可能长时间运行，漂移键 {}",
                    indexUid, REBUILD_KEYS, drifted);
        }
        int taskUid = gateway.updateSettings(indexUid, projected.toJson());
        gateway.awaitTask(taskUid, waitTimeout);
        log.info("索引 {} settings 已按投影更新（task {}）", indexUid, taskUid);
    }
}
