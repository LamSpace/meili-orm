[中文](zh-CN/limitations.md)

# meili-orm Limitations

Every item below is an empirically verified behavioral boundary (the test or sentinel that pins it is named in parentheses), and each carries a workaround. For capabilities outside the promised scope, see the [Non-Goals](../README.md) section in the README.

## 1. Connection and read timeouts are not configurable

The official SDK's `Config` builds its own `OkHttpClient` at construction time, and there is **no injection point** for timeouts or connection pooling. This was re-verified against the meilisearch-java 0.21.0 constructor surface by direct source reading: none of the `Config` constructors accepts an HTTP client.

**Workaround**: if you need fine-grained network control, construct a `com.meilisearch.sdk.Client` bean yourself — meili-orm's auto-configuration is guarded by `@ConditionalOnMissingBean` and yields to a user-supplied `Client`. Alternatively, file a feature request upstream asking the SDK to accept an injected `OkHttpClient`.

## 2. Count semantics: whole-index counting

`operations.count(Type)` calls `GET /indexes/{uid}/stats` directly and returns `numberOfDocuments`. **Filtered counting is not supported** — the `total` semantics of `POST documents/fetch` simply do not exist on that endpoint.

Also note: on the pinned server generation, the `GET documents/count` route is unusable — the request is swallowed by the `documents/{id}` route and answered as `document_not_found`, which is why counts go through `stats` instead. A live-server integration test pins this fact.

**Workaround**: for conditional counts, run `search` with an empty `q` plus a filter and offset paging via `limit(1)` (do not set `page`/`hitsPerPage` — mixing the two paging models is rejected by the query IR), then read `getEstimatedTotalHits()`.

## 3. multiSearch executes serially in v1

`multiSearch(List<MeiliQuery>, Type)` delegates to `search` one query at a time, with no concurrency (as noted in the javadoc). Result order matches the order of the input queries.

**Workaround**: parallelize at the call site, or wait for a batched-endpoint adapter in a later version.

## 4. On Boot 4 the default serialization channel is a self-built Jackson 2 instance

Core serialization in meili-orm is built on Jackson 2. A Boot 4 application's container `ObjectMapper` defaults to Jackson 3, so the default implementation finds no Jackson 2 container bean and **builds its own instance**. Behavior is correct either way, but your global Jackson 2 customizations on Boot 4 are not propagated automatically (on Boot 3, the container's Jackson 2 mapper is picked up in preference).

**Workaround**: for Jackson 3 semantics (taking over the container `ObjectMapper`), add the dependency `io.github.lamspace:meili-orm-serializer-jackson3` — "add the dependency, it takes over"; see [boot3-to-boot4.md](boot3-to-boot4.md). Alternatively, register your own `MeiliDocumentSerializer` bean and the auto-configuration yields entirely.

## 5. Writes are asynchronous by default: not queryable immediately

Every Meilisearch write (documents, settings, index creation/deletion) is an asynchronous task that returns a `taskUid` immediately. With `meili.wait-task=false` (the default), `save` returning does not mean the document is searchable.

**Workaround**: turn on `meili.wait-task=true` for write-then-read semantics (bounded by `meili.wait-timeout`), or call `operations.awaitTask(taskUid)` explicitly after saving. The APIs that return a task uid are `createIndex` / `applySettings`; `awaitTask` throws `MeiliTaskTimeoutException` on timeout.

## 6. Changing filterable/sortable triggers a full server-side rebuild

A change to `filterableAttributes` / `sortableAttributes` in settings makes Meilisearch **rebuild the entire index**: on large datasets the task takes minutes or more. `sync-settings + apply` detects drift involving these keys and prints an explicit cost WARN, but it does not block execution.

**Workaround**: day to day, keep the default `create-if-missing` (for an existing index it only reports drift, never writes); when a role change is genuinely needed, pick a migration window, switch to `sync-settings + apply` explicitly, and relax `wait-timeout`; in environments that cannot tolerate rebuild jitter, use the `fail` policy to turn drift into a startup gate and apply the change through a manual migration script.

## 7. Callback beans cannot express the target type with a lambda

The callback registry resolves the target entity from the **generic signature** of the implementation class. A lambda's type parameters are erased by the JVM, so registering one throws `MeiliMappingException` at startup — a loud failure, never a silent no-op.

**Workaround**: implement the four callback interfaces with a named class or an anonymous inner class (recommended; see the examples in the [mapping guide](mapping-guide.md)). If a lambda is truly needed, use `MeiliEntityCallbacks.register(Book.class, (BeforeConvertCallback<Book>) ...)` to pass the entity type explicitly and register into the registry yourself.

## 8. Precision boundary of server-side numeric semantics (the f64 surface)

Storage and echo-back are faithful: primary keys and numeric values written and read through the raw channel survive bit for bit — the sentinel IT `SpikeBRawJacksonPrecisionIT` keeps `9007199254740993` under permanent verification. But Meilisearch's **sorting, filter comparisons, and facet statistics work on float semantics** (server documentation states about 15 significant decimal digits): boundary behavior when very large integers take part in sort or range-filter is decided by the server and is outside meili-orm's control.

**Workaround**: carry decimal-sensitive values such as money either in a magnitude `Double` can represent exactly or in string fields; use oversized integer primary keys for equality lookups only (the primary-key path in this library is lossless), never as a range or sort key.

## 9. The SDK's transitive dependencies land on the application classpath

meilisearch-java brings okhttp (5.3.2), okio, and gson (2.13.2) onto the classpath in `api` scope — and the okhttp 5.3.2 Maven artifact is a metadata-only shell whose JVM classes live in `okhttp-jvm` (this project already handles that empty-shell problem explicitly). These can collide with the host application's okhttp3/gson version governance.

**Workaround**: the usual `<exclusions>` or a `dependencyManagement` pin. **Do not** exclude gson — the SDK's internal model parsing depends on the default `GsonJsonHandler`, as the sentinel IT `SpikeAJsonHandlerIT` verified empirically: a custom `JsonHandler` is incompatible with the SDK's internal typed models. meili-orm's own entity channel goes entirely through raw strings, but the SDK's internal steps still use Gson.

## 10. Index and document deletion obey the same async task model

`deleteIndex` / `deleteAll` / `deleteById` return when the request is accepted, not when the task reaches a terminal state. With `wait-task=true` these methods wait internally; otherwise use `awaitTask`.

---

## Repository layer

## 11. `Page.getTotalElements()` is an estimate

Meilisearch search responses report `estimatedTotalHits`/`totalHits` (whichever the server configuration produces), not an exact total, and `Page`'s paging data is computed from it. On top of that, commons `PageImpl` has its own inherent semantics: the total is raised to at least cover the current page (when offset + hits on this page is larger). Use `count()` (the stats channel, whole index) for an exact count.

**Workaround**: render user-facing copy as "about N results / more"; route exact requirements through `count()` or application-side counting.

## 12. `findAll()` / `findAll(Sort)` are truncated by maxTotalHits

The documents/fetch channel reads at most the index's `pagination.maxTotalHits` (default 1000) in a single pass. When the repository implementation fills that ceiling it logs a WARN declaring the result may be truncated — it never silently claims to have returned everything.

**Workaround**: for full traversal, page with a cursor (`findAll(PageRequest.of(n, size))`) or drive `DocumentsFetchQuery` directly through Operations.

## 13. `…Containing` / `…Like` are full-text approximations, not substring matches

Meilisearch has no substring DSL, so the repository layer renders these as a full-text `q=<value>` plus `attributesToSearchOn=[property]`. Hits are shaped by tokenization, typo tolerance, and rankingRules — e.g. `findByTitleContaining("三体")` goes through the full-text channel — so the result does not match Java's `String.contains` semantics. Like wildcards are ignored in v1.

**Workaround**: deterministic prefix/suffix matching requires server-side facilities of the BEGINS WITH kind, which are outside v1's support surface; hand-write a `MeiliQuery` through Operations, or post-filter application-side.

## 14. `deleteAll(Iterable)` / `deleteAllById(Iterable)` send one request per element

In v1 each element is its own delete request (and its own task), so bulk deletion is expensive.

**Workaround**: to clear a whole index use `deleteAll()`; watch for gateway batching in a later release.

## 15. Derived-query keywords are a subset, and the role pre-check looks only at entity declarations

Not supported: `StartingWith`/`EndingWith`/`Regex`/`Null`/`Empty`/`Exists`/`IgnoreCase`; the `Distinct` modifier; **abbreviated property names**; count/exists/delete derivations; DTO projection; `Stream` returns (the [mapping guide](mapping-guide.md) has the full list and alternative spellings). A filter/sort target property must be declared on the entity with `@MeiliField(filterable/sortable/searchable)`; a role that exists only via `@MeiliSetting` pass-through **does not satisfy the pre-check** — startup fails, and the failure message says exactly this. The entity declaration is the single source of truth for the pre-check.

**Workaround**: in pass-through scenarios, declare both sides — field annotation and settings pass-through agreeing is a deliberate redundancy check. Anything beyond the supported subset goes through a hand-written `@MeiliQuery`.

## 16. With `@MeiliQuery` present, only ordering/top-N come from the method name

Once the annotation declares the query, the remaining condition segments in the method name are ignored (a startup WARN lists them). The silent coexistence can drift into "edited the annotation, forgot the method name".

**Workaround**: on annotated methods, strip the condition segments out of the name entirely and keep only the `OrderBy`/`Top` suffixes.

---

## Entity auditing

## 17. created uses fill-if-absent semantics and never judges server-side existence

Meilisearch has no server-side timestamps, and an upsert offers no way to tell "insert" from "update" (the same primary key simply overwrites; there is no ETag/sequence-number equivalent). `@CreatedDate` is therefore implemented as "populate only when the current value is empty" (a `null` object, or the `0` sentinel for a primitive `long`): an entity built on the client that carries a non-empty created value gets that value written as-is and never rewritten — even when the row is genuinely new on the server — and re-saving an entity that already carries a value keeps the original created (`DefaultMeiliSearchOperationsAuditTest` / `MeiliAuditIT` verify this clause by clause).

**Workaround**: for strict "first write" semantics, make the determination on the business side (set audit fields only on the create path), or introduce an external row version and compare.

## 18. No createdBy-style auditing

There is no `@CreatedBy` / `@LastModifiedBy` / `AuditorAware` equivalent: the library has no authentication-context input source, and "who acted" cannot be determined within a client-side write path, so we do not invent one. Timestamp auditing is the entirety of v1 auditing.

**Workaround**: populate your own operator fields in a `BeforeConvertCallback` on the application side — callbacks run after audit population has completed, so they see the final timestamp values.
