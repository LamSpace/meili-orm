# meili-orm Mapping Guide

[中文](zh-CN/mapping-guide.md)

The division of labor in one sentence: **Jackson owns all document format conversion;
the metamodel manages exactly three things — "index name / primary key / field roles"**.
Meilisearch has no per-field mapping concept, so "mapping" lands as a **settings
projection** — field roles declared via annotations are projected into server-side
settings arrays at startup.

## Annotation reference table

| Annotation | Meilisearch concept | Key point |
|---|---|---|
| `@MeiliDocument(indexName)` | index uid | Static names only in v1; multiple entities declaring the same indexName fail at startup |
| `@MeiliId` | primary key attribute | **Required, exactly one**; type must be String or integral (`int/long/Integer/Long`); the property need not be named `id`, which inherently sidesteps the multi-candidate trap of server-side `xxxId` auto-inference |
| `@MeiliField(name)` | document field name | One name shared by serialization, deserialization, and projection; declared alongside `@JsonProperty` with mismatched values → startup failure (conflict resolution takes precedence; priority order: `@MeiliField.name` > `@JsonProperty` > Java name) |
| `@MeiliField(searchable=true, searchableOrder=n)` | `searchableAttributes` | Array order is search weight (see below) |
| `@MeiliField(filterable=true)` | `filterableAttributes` | Equality-filter semantics |
| `@MeiliField(sortable=true)` | `sortableAttributes` | Sort declaration |
| `@MeiliField(displayed=true)` | `displayedAttributes` | **Whitelist effect**: as soon as any field declares displayed, the projected array contains only declared fields — undeclared fields disappear from search hits (the primary key is exempt). Use with care |
| `@MeiliSetting(settingPath)` | full settings passthrough | Class-level, repeatable, `classpath:` prefix (see "Passthrough rules") |
| `@CreatedDate` | none (filled on the client write path) | Creation timestamp, filled only when the current value is empty; approximate semantics in "Audit fields" below |
| `@LastModifiedDate` | none (filled on the client write path) | Modification timestamp, overwritten unconditionally on every save |
| Jackson `@JsonIgnore` | field exclusion | Not persisted, not projected; no separate `@MeiliTransient` is invented |

## The semantic iron rule: no annotation = no declaration

A role array is generated **only when at least one non-primary-key field declares it**;
if nobody declares it, the key is simply absent from the projected JSON and settings
sync never overwrites the corresponding server-side default. Entities should declare
only the roles they actually use.

## searchableOrder ordering rules

Assembly order of the projected array:

1. Fields declaring `searchableOrder >= 0` come first, sorted ascending by that value;
2. all remaining searchable fields (`order = -1`, the default) follow in lexicographic
   dotted-path order.

Duplicate explicit order values within one entity throw `MeiliMappingException` at
startup. `filterableAttributes` / `sortableAttributes` / `displayedAttributes` are
always in lexicographic dotted-path order.

## Nested objects and dotted paths

Properties of non-simple types (POJO/record) are **flattened into dotted-path** leaves
during projection: `@MeiliField(filterable=true)` on `Author.city` projects as
`"author.city"`, aligning with Meilisearch's flattened retrieval semantics for nested
documents. Rules:

- Simple types (String/numeric/date/enum/array/Map, etc.) are leaves and may declare roles directly;
- expansion depth is capped at 3; self-references are cut off via a visited set;
- collections/Maps are opaque leaves — no further expansion inside them;
- an aggregate field that expands into child fields may not declare roles itself.

## @MeiliSetting passthrough rules

- The carrier is a **class-level repeatable annotation** (deliberately kept out of
  `@MeiliDocument`, separating it from field-role responsibilities);
- `settingPath` points at a classpath JSON object file; a missing file, a non-object
  root, or an unknown key all fail at startup, with the error message naming the file
  and the specific key;
- allowed-key whitelist (18 entries): `searchableAttributes`, `filterableAttributes`,
  `sortableAttributes`, `displayedAttributes`, `rankingRules`, `synonyms`, `stopWords`,
  `distinctAttribute`, `typoTolerance`, `faceting`, `pagination`, `searchCutoffMs`,
  `dictionary`, `separatorTokens`, `nonSeparatorTokens`, `proximityPrecision`,
  `embedders`, `localizedAttributes`;
- multiple `@MeiliSetting` annotations merge in declaration order; later-declared files
  overwrite earlier ones;
- **passthrough wins**: when a passthrough file declares any of the four role arrays,
  that array takes the passthrough value and overwrites the annotation projection (the
  remaining keys merge across the two channels);
- an explicit contradiction between passthrough keys and role annotations (the same
  array both projected and passed through with different values) is not an error —
  "passthrough wins" applies; this is deliberate escape-hatch semantics.

## Projection pipeline

```
 @MeiliDocument + field role annotations + @MeiliSetting passthrough files
        |
        v
 MeiliSettingsProjection (pure function, runs at startup, zero runtime overhead)
   -> searchableAttributes (by order)/filterableAttributes/
      sortableAttributes/displayedAttributes  ⊕ passthrough JSON (passthrough wins)
        |
        v   (when auto-init != none and the projection is non-empty)
 diff against the actual values from GET /indexes/{uid}/settings
   (only keys declared by the projection are compared: arrays by set equality,
     searchableAttributes by ordered equality; undeclared keys never participate in comparison, never written)
        |
        +-- no drift ------------------> DEBUG log, no-op
        +-- drift ------------------> handled per mode and policy (table below)
```

## auto-init × on-settings-drift behavior matrix

| | index absent | index present, no drift | index present, drift detected |
|---|---|---|---|
| `auto-init=none` | no interaction | no interaction | no interaction |
| `create-if-missing` (default) | create index + push projected settings + wait for task | DEBUG | **report only, never write** (warn alert; `apply` is suppressed and stated as such; `fail` throws and aborts startup) |
| `sync-settings` | same as above | DEBUG | `warn`: alert lists the drifted keys; `apply`: push the projection + wait for task, with an extra alert on the full-reindex cost when `filterableAttributes`/`sortableAttributes` are involved; `fail`: throw and abort startup |

Cost reminder: changing filterable/sortable triggers a server-side **full reindex**, so
settings sync should only happen during startup/migration windows — in production,
start with `create-if-missing` and switch explicitly to `sync-settings` + `apply`
during a change window (see the [limitations list](limitations.md)).

## The four lifecycle callbacks

| Interface | Trigger point | Signature semantics |
|---|---|---|
| `BeforeConvertCallback<T>` | before serialization in `save`/`saveAll` | returns the rewritten entity |
| `AfterSaveCallback<T>` | after the write task is accepted (when wait-task is on, through the awaited terminal state) | fired for each saved entity |
| `AfterLoadCallback<T>` | after the raw JSON is fetched, **before** deserialization | both argument and return value are document JSON text |
| `AfterConvertCallback<T>` | after entity deserialization | returns the rewritten entity |

Write chain: `(audit fill) → BeforeConvert → serialize → write request → (optional task wait) → AfterSave`;
read chain: `fetch raw JSON → AfterLoad → deserialize → AfterConvert`. Multiple callbacks
on the same chain run in registration order; the target entity type is matched via the
generic argument (supertype and subtype matches are both honored).

Declaring a bean is all it takes:

```java
@Configuration(proxyBeanMethods = false)
class BookCallbacks {

    /** Normalize the book title before the write: the generic argument is declared by a named class (lambda generics are erased by the JVM and cannot be resolved automatically). */
    static final class TrimBookTitle implements BeforeConvertCallback<Book> {
        @Override
        public Book onBeforeConvert(Book entity, String indexName) {
            return entity.title() == null ? entity : /* rebuild the record with the trimmed title */;
        }
    }

    @Bean
    BeforeConvertCallback<Book> trimBookTitle() {
        return new TrimBookTitle();
    }
}
```

## Audit fields (@CreatedDate / @LastModifiedDate)

Timestamp auditing on the client write path. **Orthogonal** to role annotations (they can
co-occur on one field without interference; an audit field still participates in name
projection and serialization as an ordinary document field, and lands in no role array
unless separately annotated for one).

| Annotation | Fill semantics |
|---|---|
| `@CreatedDate` | Stamps the current instant only when the current value is empty — `null` for object types; for a primitive `long`, `0` additionally counts as unset (sentinel value); an existing value is kept as is |
| `@LastModifiedDate` | Overwritten with the current instant unconditionally on every `save`/`saveAll` (first save coincides with created) |

- Six permitted types: `Instant` / `OffsetDateTime` / `ZonedDateTime` / `LocalDateTime` /
  `long` / `Long`. `long`/`Long` store epoch milliseconds; the temporal types wrap the
  same instant with the system-zone offset. Any type outside the permit set throws
  `MeiliMappingException` during entity resolution (the message names the class and field).
- Fill point: in `save`/`saveAll`, after entity resolution and **before
  `BeforeConvertCallback`** — callbacks and serialization see the final value; the fill
  always precedes all user callbacks and cannot be reordered by them. Read paths and
  delete operations never touch audit fields.
- Write-back has two shapes: a POJO is written in place and the **same instance** is
  returned; a record is rebuilt as a new instance through its canonical constructor,
  with non-audit component values preserved one by one. Rebuilding **re-runs** the
  record's compact constructor — so constructor validation must be idempotent (which
  holds naturally for legitimate filled values); after an audit fill, the rebuild
  necessarily passes through that constructor again.
- Effective only at the entity top level; audit fields inside nested objects are not filled.
- An entity with no audit annotations at all keeps the exact save path from before this
  capability existed (same instance, zero reflection, no rebuild overhead).

> ⚠️ Approximate semantics: Meilisearch has no server-side timestamps, and upsert cannot
> distinguish "insert vs update", so `@CreatedDate` is a client-side **fill-if-empty**
> approximation that never checks server-side existence — an entity created client-side
> but carrying a non-empty created value is written through unchanged. Edge cases:
> items 17–18 of the [limitations list](limitations.md).

## Startup fail-fast validation checklist

Any of the following throws `MeiliMappingException` at application startup (the message
locates the class/field/file); nothing is left as a runtime surprise:

- primary key missing, declared more than once, or not of type String/integral;
- multiple entities declaring the same `indexName`;
- `@MeiliField.name` conflicting with `@JsonProperty`;
- duplicate explicit `searchableOrder` values;
- a `@CreatedDate`/`@LastModifiedDate` field whose type is outside the six-type permit
  set (see "Audit fields");
- a passthrough file that is missing, not a JSON object, or contains a non-whitelisted key;
- a callback bean whose target entity generic cannot be resolved (lambda form).

---

## Repository layer: derived queries and `@MeiliQuery`

> The capability is opt-in: adding the `meili-orm-repository` coordinate to an
> application enables it automatically (no annotation needed); `@EnableMeiliRepositories`
> may also be used to pin the scan packages explicitly. The switch property is
> `meili.repositories.enabled` (default true).

### Method-name grammar

```
<verb>[Top<N>|First<N>][Distinct]By<condition-chain>[OrderBy<property>(Asc|Desc)[And…]]
```

Verb support: `find` / `read` / `get` / `retrieve` (and extended forms such as
`findPageBy…`); `count…By` / `exists…By` / `delete…By` derivations are **not supported**.
The condition chain is delimited by capitalized `And` / `Or`; `And` binds tighter than
`Or` (OR groups are automatically parenthesized when rendered).

### Keyword table (supported surface)

| Method-name fragment | Rendered result | Args |
|---|---|---|
| `…Equals` / `…Is` / bare property | `path = value` | 1 |
| `…Not` (after the property) | `path != value` | 1 |
| `Not…` (before the property) | `NOT (path = value)` | 1 |
| `…In` | `path IN [v1, v2]` (an empty collection returns an empty result directly, no request sent) | 1 (collection/array) |
| `…Between` | `path BETWEEN a AND b` (closed on both ends) | 2 |
| `…GreaterThan` / `…After` | `path > value` | 1 |
| `…GreaterThanEqual` | `path >= value` | 1 |
| `…LessThan` / `…Before` | `path < value` | 1 |
| `…LessThanEqual` | `path <= value` | 1 |
| `…True` / `…False` | `path = true` / `path = false` | 0 |
| `…Containing` / `…Like` | full-text `q=value` + `attributesToSearchOn=[path]` (Like wildcards are ignored, equivalent to Containing; at most one per method) | 1 |
| `findTop<N>` / `findFirst<N>` | `limit(N)` (when a `Pageable` is also present, paging takes precedence and a WARN is logged) | — |
| `OrderBy…Asc/Desc` | `sort("path:asc|desc")`, followed by the ordering from `Pageable`/`Sort` | — |

String values always render as **escaped double-quoted literals** (`"` and `\` get a
backslash); numbers and booleans render bare; `LocalDate`/`LocalDateTime`/
`OffsetDateTime`/`Instant` render bare as ISO text. A condition value that is `null`
at call time fails immediately (optional criteria are not supported).

### Unsupported surface (rejected at startup, message names the method)

`StartingWith`, `EndingWith`, `RegularExpression`, `IsNull`, `IsNotNull`, `IsEmpty`,
`IsNotEmpty`, `Exists`, `IgnoreCase`, the `Distinct` modifier (distinct requires a
property — use `@MeiliQuery(distinct=…)` instead), **property abbreviations** (e.g.
`findByAdrCity` — names are split against the entity field dictionary by longest prefix;
abbreviations are never guessed), equality conditions on collection/object properties,
and DTO projections or `Stream` return types.

Supported return types: `List<T>`, `Optional<T>` (the first hit wins, DEBUG logged),
`Page<T>` (a `Pageable` argument is mandatory; `getTotalElements()` is a server-side
estimate, and `PageImpl` carries the inherent clamping behavior "total at least covers
the current page").

### Projection-name bridging (method-name property → document field)

Property chains in conditions and sorts are split segment by segment against the
**entity's Java field dictionary** using longest prefix (`AuthorCity` → `author` →
`city`), and each segment resolves to a projected path in the core metamodel:
`@MeiliField(name="book_title")` makes `findByTitle…` render `book_title = …`; nested
objects render as dotted paths (`author.city`). `@JsonIgnore` fields, `static` fields,
unknown properties, and aggregate properties used as condition targets all fail at
startup, located by method.

### Startup role preflight

| Condition family | Entity must declare |
|---|---|
| properties entering the filter (equality/IN/range/comparison/boolean/NOT) | `@MeiliField(filterable = true)` |
| properties entering the sort (`OrderBy`) | `@MeiliField(sortable = true)` |
| `Containing`/`Like` target properties | `@MeiliField(searchable = true)` |

Any missing declaration → startup failure (`MeiliMappingException`), with the message
offering two fixes: add the declaration to the field annotation, or declare it
server-side via `@MeiliSetting` passthrough. **The preflight's source of truth is always
the entity declaration** — roles declared only in passthrough JSON never enter the
preflight input (guarding against silent divergence between entity and server; if a
role is declared only via passthrough, add the field annotation as well). Conditions on
the primary-key property are exempt from the filterable preflight (the server can
address documents by primary key).

### `@MeiliQuery` annotated queries

```java
@MeiliQuery(q = ":keyword", filter = "genre = :genre AND price > ?0", distinct = "authorId")
List<Book> brutal(@Param("genre") String genre, Double minPrice, @Param("keyword") String kw,
                  Pageable pageable);
```

- Three placeholder forms: `?N` (numbering counts only "value parameters" —
  `Pageable`/`Sort` arguments do not consume slots), `:name` (via `@Param` or compiled
  parameter names), and `#{…}` native SpEL (variables referenced as `#name` / `#argN`).
- String values in the `filter` template are quoted and escaped automatically — template
  authors **never write quotes** (`genre = :g`), so a parameter value cannot alter the
  DSL structure; numbers/booleans render bare; values in the `q` template pass through
  verbatim as the full-text query text.
- `distinct` accepts only a literal projected path (no placeholders).
- When an annotated query coexists with a parsable method name, the annotation
  short-circuits the method-name conditions: `OrderBy` and `TopN` are still taken from
  the method name; the remaining condition segments are ignored with a startup WARN.
- Startup validation: template non-empty, placeholders bindable, parentheses balanced
  in `filter`; server-side DSL syntax errors propagate as-is via
  `MeiliIndexAccessException`.

### Unbounded reads and batch delete

`findAll()` / `findAll(Sort)` go through the documents/fetch channel and are bounded by
the index's `pagination.maxTotalHits` (default 1000): filling the quota exactly logs a
WARN declaring that the result may be truncated. `deleteAll(Iterable)` /
`deleteAllById(Iterable)` delete documents one by one (one request per entity).
