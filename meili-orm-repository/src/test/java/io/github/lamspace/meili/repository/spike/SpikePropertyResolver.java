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
 * spike prototype: property-segment resolver for the structural-bridge route. Consumes only
 * "Java property segment name strings" (from {@code Part.getPropertyParts()}), fully bypassing
 * commons' {@code PropertyPath}/{@code PersistentPropertyPath} property model — reflection plus
 * core's {@link MeiliNames#docName} assemble the dotted document path segment by segment,
 * proving the single-source/two-generation feasibility of "grammar in commons, semantics in
 * core". The production implementation follows the same rules in src/main.
 */
final class SpikePropertyResolver {

    /** Maximum nesting depth, matching core's flattening depth. */
    private static final int MAX_DEPTH = 3;

    private SpikePropertyResolver() {
    }

    /**
     * Resolves a Java property-segment chain into a dotted document path.
     *
     * @param rootType entity class
     * @param segments Java property segments parsed from the method name (e.g. ["author","city"])
     * @return document path (e.g. {@code author.city})
     * @throws IllegalArgumentException on any resolution failure (unknown segment / excluded segment / chain broken on a leaf)
     */
    static String resolve(Class<?> rootType, String[] segments) {
        if (segments == null || segments.length == 0 || segments.length > MAX_DEPTH + 1) {
            throw new IllegalArgumentException("Invalid property segment chain: " + String.join(".",
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
                    throw new IllegalArgumentException("Property chain breaks on a simple type: "
                            + current.getSimpleName() + "." + field.getName());
                }
                current = field.getType();
            } else if (!MeiliNames.isSimpleType(field.getType())) {
                throw new IllegalArgumentException("Aggregate property cannot be a query target: "
                        + current.getSimpleName() + "." + field.getName());
            }
        }
        return String.join(".", docSegments);
    }

    /** Finds a queryable field by Java name (first letter case-insensitive) walking the class hierarchy. */
    private static Field findField(Class<?> type, String javaName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase(javaName)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                            || f.isAnnotationPresent(JsonIgnore.class)) {
                        throw new IllegalArgumentException("Property is not queryable (static/synthetic/@JsonIgnore): "
                                + c.getSimpleName() + "." + f.getName());
                    }
                    return f;
                }
            }
        }
        throw new IllegalArgumentException("Entity has no such Java property: " + type.getSimpleName() + "." + javaName);
    }
}
