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
package io.github.lamspace.meili.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

/**
 * Domain-centric repository abstraction for MeiliSearch indexes, binding one entity type
 * (declared via {@code @MeiliDocument}) to one index and exposing Spring Data standard
 * CRUD, paging and sorting operations plus method-name-derived queries.
 *
 * <p><b>Responsibility.</b> This interface is a declarative facade; it declares no methods
 * of its own. Every inherited operation is executed by a {@code SimpleMeiliRepository}
 * instance that delegates to {@code MeiliSearchOperations}, so all cross-cutting contracts
 * of the core layer hold unchanged: the entity metamodel is the single source of truth for
 * index name and primary key, the pluggable document serializer owns entity↔JSON conversion,
 * entity lifecycle callbacks fire on every read and write path, and write visibility obeys
 * the configured task-waiting switch.
 *
 * <p><b>Server-model caveats.</b> MeiliSearch differs from a transactional store in ways
 * that are visible through this interface:
 * <ul>
 *   <li>{@code save} is an upsert under the entity's primary key; a {@code null} key fails
 *       fast instead of letting the server guess an id.</li>
 *   <li>Writes enqueue asynchronous server tasks. Unless task waiting is enabled, an index
 *       read issued immediately after {@code save} may not yet observe the document.</li>
 *   <li>{@code findAll()} reads through the document-fetch channel and is capped by the
 *       index's {@code maxTotalHits} pagination setting (1000 by default); on truncation it
 *       returns the capped set and logs a warning rather than pretending completeness.</li>
 *   <li>{@code Page#getTotalElements()} on paged queries reflects the server's estimated
 *       hit count, not an exact total.</li>
 *   <li>{@code deleteAll(Iterable)} is executed as one delete request per entity.</li>
 * </ul>
 *
 * <p><b>Threading.</b> Implementations are stateless, thread-safe singletons intended for
 * concurrent use from any application thread; they hold no per-invocation state.
 *
 * <p><b>Availability.</b> Repositories of this kind are registered only when the optional
 * repository module is on the classpath and the enabling property is not switched off;
 * declaring this interface as a super-interface of a user interface is the trigger for
 * that registration.
 *
 * @param <T>  the domain entity type, annotated with {@code @MeiliDocument}
 * @param <ID> the type of the entity's primary key, {@code String} or an integral type
 */
public interface MeiliRepository<T, ID>
        extends CrudRepository<T, ID>, ListPagingAndSortingRepository<T, ID> {
}
