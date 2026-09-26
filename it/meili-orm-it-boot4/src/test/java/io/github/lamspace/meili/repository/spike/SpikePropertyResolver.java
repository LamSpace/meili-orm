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
package io.github.lamspace.meili.repository.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.lamspace.meili.core.mapping.MeiliNames;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Spike prototype: the structural bridge's property-segment resolver. Consumes only "Java
 * property-segment name strings" (from {@code Part.getPropertyParts()}), bypassing commons'
 * {@code PropertyPath}/{@code PersistentPropertyPath} property model entirely, and assembles the
 * document dotted path segment by segment via reflection + core's {@link MeiliNames#docName} —
 * proving the one-source-file, two-generation feasibility of "syntax to commons, semantics to
 * core". The formal implementation lands in src/main under the same rules.
 */
final class SpikePropertyResolver {

    /** Maximum nesting depth, matching core's flattening depth. */
    private static final int MAX_DEPTH = 3;

    private SpikePropertyResolver() {
    }

    /**
     * Resolves a Java property-segment chain into a document dotted path.
     *
     * @param rootType the entity class
     * @param segments the Java property segments parsed from the method name (e.g. ["author","city"])
     * @return the document path (e.g. {@code author.city})
     * @throws IllegalArgumentException on any resolution failure (unknown segment / excluded segment / chain broken on a leaf)
     */
    static String resolve(Class<?> rootType, String[] segments) {
        if (segments == null || segments.length == 0 || segments.length > MAX_DEPTH + 1) {
            throw new IllegalArgumentException("illegal property-segment chain: " + String.join(".",
                    segments == null ? new String[0] : segments));
        }
        List<String> docSegments = new ArrayList<>(segments.length);
        Class<?> current = rootType;
        for (int i = 0; i < segments.length; i++) {
            Field field = findField(current, segments[i]);
            boolean last = i == segments.length - 1;
            docSegments.add(MeiliNames.docName(field, field.getName()));
            if (!last) {
                if (MeiliNames.isSimpleType(field.getType())) {
                    throw new IllegalArgumentException("property chain breaks on a simple type: "
                            + current.getSimpleName() + "." + field.getName());
                }
                current = field.getType();
            } else if (!MeiliNames.isSimpleType(field.getType())) {
                throw new IllegalArgumentException("aggregate property cannot be a query target: "
                        + current.getSimpleName() + "." + field.getName());
            }
        }
        return String.join(".", docSegments);
    }

    /** Finds the queryable field along the class hierarchy by Java name (first-letter case-insensitive). */
    private static Field findField(Class<?> type, String javaName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase(javaName)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                            || f.isAnnotationPresent(JsonIgnore.class)) {
                        throw new IllegalArgumentException("property is not queryable (static/synthetic/@JsonIgnore): "
                                + c.getSimpleName() + "." + f.getName());
                    }
                    return f;
                }
            }
        }
        throw new IllegalArgumentException("the entity has no such Java property: " + type.getSimpleName() + "." + javaName);
    }
}
