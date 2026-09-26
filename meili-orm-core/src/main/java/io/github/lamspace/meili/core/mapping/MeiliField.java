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
package io.github.lamspace.meili.core.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the document-field name and the settings roles of a property. Roles project
 * onto MeiliSearch settings arrays — {@code searchableAttributes},
 * {@code filterableAttributes}, {@code sortableAttributes}, {@code displayedAttributes}
 * — under the hard rule <em>not annotated = not declared</em>: an array is emitted only
 * if at least one property declares that role, and declaring
 * {@code displayedAttributes} turns it into a server-side whitelist that hides every
 * other document field.
 *
 * <p>Applicable to fields only (record components propagate it to their backing field).
 * Role flags on a property that gets flattened into children are rejected at parse time;
 * annotate the leaf instead. Field exclusion is Jackson's job: use
 * {@code @JsonIgnore}, there is deliberately no transient flag here.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeiliField {

    /**
     * Projection name of the document field. Conflicting with a
     * {@code @JsonProperty} value fails entity parsing; leaving both blank keeps the
     * Java name (possibly re-named by an active Jackson naming strategy, which this
     * name does not react to).
     *
     * @return the document field name, or {@code ""} for "derive it"
     */
    String name() default "";

    /**
     * Whether the field participates in {@code searchableAttributes}.
     *
     * @return {@code true} to project as searchable
     */
    boolean searchable() default false;

    /**
     * Position weight inside {@code searchableAttributes}; searchable fields without an
     * explicit order are appended in lexicographic path order. Values must be distinct
     * among explicitly ordered searchable fields of one entity.
     *
     * @return the explicit order, or {@code -1} for "no explicit position"
     */
    int searchableOrder() default -1;

    /**
     * Whether the field participates in {@code filterableAttributes} (default equality
     * granularity). Changing this setting server-side triggers a full index rebuild.
     *
     * @return {@code true} to project as filterable
     */
    boolean filterable() default false;

    /**
     * Whether the field participates in {@code sortableAttributes}. Changing this
     * setting server-side triggers a full index rebuild.
     *
     * @return {@code true} to project as sortable
     */
    boolean sortable() default false;

    /**
     * Whether the field participates in {@code displayedAttributes}. Once any field
     * declares it, the array acts as a whitelist server-side.
     *
     * @return {@code true} to project as displayed
     */
    boolean displayed() default false;
}
