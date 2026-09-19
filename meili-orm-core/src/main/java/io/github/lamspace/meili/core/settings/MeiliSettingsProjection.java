package io.github.lamspace.meili.core.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.mapping.MeiliPersistentProperty;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mapping layer's "MappingBuilder" equivalent: a pure function from a parsed
 * {@link MeiliPersistentEntity} to a {@link ProjectedSettings} document — it reads
 * annotations and passthrough files, writes nothing anywhere, and performs no I/O beyond
 * classpath resource loading.
 *
 * <p>Projection semantics:
 * <ul>
 *   <li><em>Not annotated = not declared</em>: a role array appears only when at least
 *       one (non-id) property declares it; the primary key never participates;</li>
 *   <li>searchable order: explicit {@code searchableOrder >= 0} first ascending, the rest
 *       lexicographically by dotted path; the other three arrays always lexicographic;</li>
 *   <li>passthrough files (repeatable {@code @MeiliSetting}) are parsed, checked against
 *       the known-settings whitelist, merged in declaration order, and win over projected
 *       keys of the same name;</li>
 *   <li>every validation failure is a {@link MeiliMappingException} naming the file and
 *       the offending key, raised before any transport could observe the result.</li>
 * </ul>
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class MeiliSettingsProjection {

    /** Reader for passthrough documents; values kept as plain JDK trees for the whitelist. */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Resource prefix accepted by {@code settingPath}. */
    private static final String CLASSPATH_PREFIX = "classpath:";

    /** Every settings key a passthrough file may declare (the four role arrays included). */
    private static final Set<String> WHITELIST = Set.of(
            "searchableAttributes", "filterableAttributes", "sortableAttributes", "displayedAttributes",
            "rankingRules", "synonyms", "stopWords", "distinctAttribute", "typoTolerance",
            "faceting", "pagination", "searchCutoffMs", "dictionary", "separatorTokens",
            "nonSeparatorTokens", "proximityPrecision", "embedders", "localizedAttributes");

    /**
     * Creates the stateless projector.
     */
    public MeiliSettingsProjection() {
    }

    /**
     * Derives the settings document of one entity.
     *
     * @param entity parsed metamodel (its setting paths are resolved now)
     * @return immutable projection; {@link ProjectedSettings#hasAny()} reports whether
     *         anything was declared at all
     * @throws MeiliMappingException on unknown passthrough keys, unreadable/missing files,
     *         or malformed JSON
     */
    public ProjectedSettings project(MeiliPersistentEntity entity) {
        List<MeiliPersistentProperty> props = entity.getProperties();
        List<String> searchable = searchablePaths(props);
        List<String> filterable = rolePaths(props, MeiliPersistentProperty::isFilterable);
        List<String> sortable = rolePaths(props, MeiliPersistentProperty::isSortable);
        List<String> displayed = rolePaths(props, MeiliPersistentProperty::isDisplayed);
        Map<String, Object> passthrough = mergePassthrough(entity);
        return new ProjectedSettings(searchable, filterable, sortable, displayed, passthrough);
    }

    /**
     * Orders searchable properties: explicit weights ascending, then the rest by path.
     *
     * @param props all entity properties
     * @return ordered paths, or {@code null} when nothing declares searchable
     */
    private static List<String> searchablePaths(List<MeiliPersistentProperty> props) {
        List<MeiliPersistentProperty> declared = new ArrayList<>();
        for (MeiliPersistentProperty p : props) {
            if (p.isSearchable() && !p.isId()) {
                declared.add(p);
            }
        }
        if (declared.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        declared.stream()
                .filter(p -> p.getSearchableOrder() >= 0)
                .sorted(Comparator.comparingInt(MeiliPersistentProperty::getSearchableOrder))
                .forEach(p -> out.add(p.getJsonPath()));
        declared.stream()
                .filter(p -> p.getSearchableOrder() < 0)
                .sorted(Comparator.comparing(MeiliPersistentProperty::getJsonPath))
                .forEach(p -> out.add(p.getJsonPath()));
        return List.copyOf(out);
    }

    /**
     * Collects a plain lexicographic role array.
     *
     * @param props all entity properties
     * @param role  the role predicate
     * @return sorted paths, or {@code null} when nothing declares the role
     */
    private static List<String> rolePaths(List<MeiliPersistentProperty> props,
                                          java.util.function.Predicate<MeiliPersistentProperty> role) {
        List<String> out = new ArrayList<>();
        for (MeiliPersistentProperty p : props) {
            if (!p.isId() && role.test(p)) {
                out.add(p.getJsonPath());
            }
        }
        if (out.isEmpty()) {
            return null;
        }
        out.sort(Comparator.naturalOrder());
        return List.copyOf(out);
    }

    /**
     * Reads and merges every declared passthrough file, whitelist-checking each key.
     *
     * @param entity metamodel supplying the ordered resource paths
     * @return merged map in declaration order (later files override earlier ones)
     */
    private static Map<String, Object> mergePassthrough(MeiliPersistentEntity entity) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String settingPath : entity.getSettingPaths()) {
            Map<String, Object> doc = readSettingsFile(settingPath);
            for (String key : doc.keySet()) {
                if (!WHITELIST.contains(key)) {
                    throw new MeiliMappingException("settings 透传文件 " + settingPath
                            + " 含未知键 " + key);
                }
            }
            merged.putAll(doc);
        }
        return merged;
    }

    /**
     * Loads one classpath settings resource as a JSON object.
     *
     * @param settingPath declaration verbatim ({@code classpath:} prefix optional)
     * @return parsed document in encounter order
     * @throws MeiliMappingException when the resource is absent or not a JSON object
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readSettingsFile(String settingPath) {
        String resource = settingPath.startsWith(CLASSPATH_PREFIX)
                ? settingPath.substring(CLASSPATH_PREFIX.length()) : settingPath;
        while (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = MeiliSettingsProjection.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new MeiliMappingException("settings 透传文件缺失: " + settingPath);
            }
            Object parsed = JSON.readValue(in, Map.class);
            return (Map<String, Object>) parsed;
        } catch (MeiliMappingException e) {
            throw e;
        } catch (Exception e) {
            throw new MeiliMappingException("settings 透传文件解析失败: " + settingPath, e);
        }
    }
}
