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
package io.github.lamspace.meili.repository.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliNames;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Java-property-path to document-path bridge backed by the entity's own field declarations.
 *
 * <p><b>Responsibility.</b> Resolves the property chain a query method talks about (either a
 * camel-concatenated chain such as {@code AuthorCity} or a dotted path such as
 * {@code author.city}) into the document projection path owned by the core metamodel naming
 * rules (renames via {@code @MeiliField.name}, nested dot paths). The core metamodel is the
 * single source of truth for field names: this class only walks Java fields and reuses
 * {@link MeiliNames#docName}, so a renamed or nested property resolves identically to what
 * the settings projection and the serializer emit — the two can never disagree.
 *
 * <p><b>Grammar boundary.</b> Segment splitting is longest-prefix matching against the
 * declared field dictionary of the entity type; <em>abbreviation restoration is explicitly
 * not supported</em> (a miss throws with "abbreviations are not supported" in the message
 * rather than guessing). Fields
 * that are {@code static}, synthetic, or {@code @JsonIgnore}-excluded are unqueryable and
 * reported as such; an aggregate (non-leaf) property used as a query target is rejected so a
 * caller can never filter on an object that the server sees only as flattened children.
 *
 * <p><b>Thread model.</b> Stateless; every call resolves independently. Field dictionaries
 * are rebuilt per call (reflection over the entity class); resolution happens once at
 * bootstrap for derived methods, so the cost is not on the query path.
 */
public final class MeiliPropertyPaths {

    /** Maximum nesting depth accepted, mirroring the core flattening cap. */
    private static final int MAX_DEPTH = 3;

    /** Utility — no instances. */
    private MeiliPropertyPaths() {
    }

    /**
     * Resolves a camel-concatenated Java property chain (segments from a derived method name,
     * e.g. {@code ["Author","City"]}) into the entity's document path (e.g. {@code author.city}).
     *
     * @param domainType the entity class owning the declarations
     * @param methodName the repository method name, for error location
     * @param segments   Java property segments in declaration order
     * @return the dotted document path
     * @throws IllegalArgumentException when a segment cannot be resolved, the chain breaks on
     *                                  a simple type, or the leaf is an aggregate; the message
     *                                  names the method, the offending segment and the entity
     */
    public static String resolveChain(Class<?> domainType, String methodName, List<String> segments) {
        if (segments == null || segments.isEmpty() || segments.size() > MAX_DEPTH + 1) {
            throw new IllegalArgumentException("Invalid derived-query property segment chain (method " + methodName + ", entity "
                    + domainType.getSimpleName() + "): "
                    + String.join(".", segments == null ? List.of() : segments));
        }
        List<String> docSegments = new ArrayList<>(segments.size());
        Class<?> current = domainType;
        for (int i = 0; i < segments.size(); i++) {
            Field field = findField(current, segments.get(i), domainType, methodName);
            boolean last = i == segments.size() - 1;
            docSegments.add(MeiliNames.docName(field, field.getName()));
            if (!last) {
                if (MeiliNames.isSimpleType(field.getType())) {
                    throw new IllegalArgumentException("Method " + methodName + "'s property chain breaks on a simple type: "
                            + current.getSimpleName() + "." + field.getName() + " (entity "
                            + domainType.getSimpleName() + ")");
                }
                current = field.getType();
            } else if (!MeiliNames.isSimpleType(field.getType())) {
                throw new IllegalArgumentException("Method " + methodName + " targets an aggregate property: "
                        + current.getSimpleName() + "." + field.getName()
                        + " (entity " + domainType.getSimpleName() + "; declare nested objects down to leaf properties, e.g. "
                        + field.getName() + "SomeLeaf)");
            }
        }
        return String.join(".", docSegments);
    }

    /**
     * Resolves a dotted Java property path (as carried by {@code Sort.Order} or
     * {@code Pageable}, e.g. {@code author.city}) into the document path. Each dotted segment
     * must name a declared property exactly (case-insensitive first letter); camel
     * concatenation is not applied here.
     *
     * @param domainType the entity class owning the declarations
     * @param source     description of the requesting construct, for error location
     * @param dottedPath dot-separated Java property path
     * @return the dotted document path
     * @throws IllegalArgumentException when any segment cannot be resolved
     */
    public static String resolveDotted(Class<?> domainType, String source, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        List<String> segments = new ArrayList<>(parts.length);
        for (String part : parts) {
            segments.add(part);
        }
        return resolveChain(domainType, source, segments);
    }

    /**
     * Splits a camel-concatenated property chain (as written in a derived method name, e.g.
     * {@code AuthorCity}) into Java property segments by longest-prefix matching against the
     * declared field dictionary of the entity type (walking nested types as it consumes).
     *
     * @param domainType entity class providing the dictionary
     * @param source     requesting method name, for error location
     * @param rawPath    camel-concatenated chain
     * @return Java property segments in order (e.g. {@code ["author", "city"]})
     * @throws IllegalArgumentException when a prefix does not match any declared property —
     *                                  abbreviations are never guessed and the message says so
     */
    public static List<String> splitCamel(Class<?> domainType, String source, String rawPath) {
        List<String> chain = new ArrayList<>();
        Class<?> current = domainType;
        int pos = 0;
        while (pos < rawPath.length()) {
            String rest = rawPath.substring(pos);
            java.lang.reflect.Field match = null;
            for (java.lang.reflect.Field f : declared(current)) {
                if (rest.regionMatches(true, 0, f.getName(), 0, f.getName().length())
                        && (match == null || f.getName().length() > match.getName().length())) {
                    match = f;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException("Cannot resolve property segment (abbreviations are not supported): method " + source
                        + " property " + rawPath + " matches nothing on entity " + domainType.getSimpleName());
            }
            chain.add(match.getName());
            pos += match.getName().length();
            current = match.getType();
        }
        return chain;
    }

    /**
     * Declared queryable fields of a type walking the hierarchy (static/synthetic skipped).
     *
     * @param type starting class
     * @return candidate fields for dictionary matching
     */
    private static List<java.lang.reflect.Field> declared(Class<?> type) {
        List<java.lang.reflect.Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    /**
     * Returns the projected document name of the entity's primary-key property.
     *
     * @param domainType the entity class (must carry a valid {@code @MeiliId})
     * @return the primary-key document path
     * @throws io.github.lamspace.meili.core.exception.MeiliMappingException when the entity
     *         mapping itself is invalid
     */
    public static String idPath(Class<?> domainType) {
        return io.github.lamspace.meili.core.mapping.MeiliPersistentEntity.of(domainType)
                .getIdProperty().getJsonPath();
    }

    /**
     * First case-insensitive exact-name field hit along the class hierarchy.
     *
     * @param type       current class in the walk
     * @param javaName   Java property segment name
     * @param domainType root entity for error location
     * @param methodName requesting method for error location
     * @return matched field
     */
    private static Field findField(Class<?> type, String javaName, Class<?> domainType, String methodName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase(javaName)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                            || f.isAnnotationPresent(JsonIgnore.class)) {
                        throw new IllegalArgumentException("Method " + methodName + " references an unqueryable property ("
                                + "static/synthetic/@JsonIgnore): " + domainType.getSimpleName()
                                + "." + f.getName());
                    }
                    return f;
                }
            }
        }
        throw new IllegalArgumentException("Cannot resolve property segment (abbreviations are not supported): method " + methodName + " property "
                + javaName + " does not exist on entity " + domainType.getSimpleName());
    }
}
