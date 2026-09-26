# Meili-ORM

<div align="center">

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE) [![CI](https://github.com/LamSpace/Meili-ORM/actions/workflows/verify.yml/badge.svg)](https://github.com/LamSpace/Meili-ORM/actions/workflows/verify.yml) [![Java](https://img.shields.io/badge/Java-17%2B-orange)](#-build--test) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x%20%7C%204.x-brightgreen)](docs/boot3-to-boot4.md) [![Meilisearch](https://img.shields.io/badge/Meilisearch-v1.x-ff59a1)](https://www.meilisearch.com/docs)

[中文](README.zh-CN.md)

</div>

A Spring Data Elasticsearch–style **Spring Boot starter for [Meilisearch](https://www.meilisearch.com/)**,
built on top of the official Java SDK (`com.meilisearch.sdk:meilisearch-java`):
annotation-driven mapping, automatic settings projection, templated operations and
auto-configuration — **one jar serving both Spring Boot 3.5.x and 4.x**.

## ⚡ At a Glance

Annotate a record, inject `MeiliSearchOperations`, search. The starter projects your field-role
annotations into Meilisearch settings at startup, keeps SDK types out of your business code,
loses no `Long` primary-key precision (raw JSON channel), and runs on one artifact across two
Spring Boot generations.

**Why not the plain SDK?** The SDK gives you HTTP bindings; it does not give you entity mapping,
settings management, task-aware write semantics, or Spring wiring. **Why not
`spring-data-meilisearch`?** That community project re-implements Spring Data internals; Meili-ORM
mirrors the Spring Data Elasticsearch *programming model* (Operations template + optional
repository layer) while staying a third-party starter with a smaller contract surface.

## 📦 Install

> **Not yet published to Maven Central.** Until the first release, install from source:

```bash
git clone https://github.com/LamSpace/Meili-ORM.git
cd Meili-ORM
mvn -DskipTests install
```

Then add the starter:

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>spring-boot-starter-meili-orm</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Three lines of configuration:

```yaml
meili:
  url: http://localhost:7700
  api-key: masterKey-xxxxxxxx
  wait-task: true          # write-then-read consistency (Meilisearch writes are async tasks)
```

## 🚀 Quick Start

```java
@MeiliDocument(indexName = "books")
public record Book(
        @MeiliId Long id,
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
        @MeiliField(filterable = true) String genre,
        @MeiliField(filterable = true, sortable = true) Double price) {
}

@Service
class BookService {
    private final MeiliSearchOperations operations;      // auto-configured

    void importOne(Book book) {
        operations.save(book);                           // upsert; queryable on return under wait-task
    }

    List<Book> search(String q) {
        return operations.search(MeiliQuery.query(q)
                .filter("genre = \"科幻\"")
                .sort("price:asc")
                .page(1).hitsPerPage(10)
                .facets("genre"), Book.class)
                .getHits();                              // Long primary keys bit-exact (raw channel)
    }
}
```

At startup, `IndexInitializer` creates the index and pushes the annotation-projected settings
according to `meili.index.auto-init`; drift policies are covered in the
[mapping guide](docs/mapping-guide.md).

### 🗂 Repository-style access (opt-in coordinate)

Beyond the template, add the repository artifact explicitly (**not aggregated by the starter**):

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>meili-orm-repository</artifactId>
    <version><!-- same version as the other meili-orm coordinates --></version>
</dependency>
```

Declare an interface and it becomes a bean (auto-scanned under your application package;
or point at packages with `@EnableMeiliRepositories`):

```java
public interface BookRepository extends MeiliRepository<Book, Long> {

    List<Book> findByGenreAndPriceGreaterThan(String genre, Double min);  // derived → filter DSL

    List<Book> findByTitleContaining(String t);                            // → full-text q + scoped attributes

    Page<Book> findPageByGenreOrderByPriceAsc(String genre, Pageable pg);  // paging (totals are estimates)

    @MeiliQuery(filter = "price BETWEEN :lo AND :hi")                      // annotation escape hatch
    List<Book> inRange(@Param("lo") Double lo, @Param("hi") Double hi);
}
```

Property names in method names are bridged through the entity projection (`title` → `book_title`);
filter/sort targets must declare the corresponding role, otherwise startup **fails fast** with a
fix hint. The full keyword table and preflight rules are in the
[mapping guide](docs/mapping-guide.md); semantic boundaries in [limitations](docs/limitations.md)
items 11–16.

## ✨ Features

| Capability | In one line | Details |
|---|---|---|
| Annotation mapping | `@MeiliDocument` / `@MeiliId` / `@MeiliField` roles / `@MeiliSetting` passthrough; `@JsonIgnore` for exclusion | [mapping guide](docs/mapping-guide.md) |
| Settings projection | Field roles → searchable/filterable/sortable/displayed arrays; iron rule *no annotation = no declaration* | [mapping guide](docs/mapping-guide.md) |
| Index auto-initialization | `auto-init=none / create-if-missing / sync-settings` × `on-settings-drift=warn / apply / fail` | [mapping guide](docs/mapping-guide.md) |
| Templated operations | Documents CRUD, search/multiSearch, index & settings management, task awaiting | javadoc of `MeiliSearchOperations` |
| Typed query IR | `MeiliQuery`: filter DSL + groups, sort, two paging styles, facets, hybrid, `raw` escape hatch — no SDK leakage | javadoc of `MeiliQuery` |
| Lifecycle callbacks | `BeforeConvert` / `AfterSave` / `AfterLoad` / `AfterConvert`, declared beans take effect | [mapping guide](docs/mapping-guide.md) |
| Timestamp auditing | `@CreatedDate` (fill-if-empty) / `@LastModifiedDate` (always); illegal types fail startup | [limitations](docs/limitations.md) items 17–18 |
| Pluggable serialization | `MeiliDocumentSerializer` interface; Jackson 2 default; optional Jackson 3 module for Boot 4 | [Boot 3 → 4 guide](docs/boot3-to-boot4.md) |
| Repository layer (opt-in) | `MeiliRepository` with CRUD, derived queries, `@MeiliQuery`, startup role preflight | [mapping guide](docs/mapping-guide.md) |
| Testcontainers integration (opt-in) | Typed `MeiliSearchContainer` + `@ServiceConnection` bridge for integration tests | [testcontainers guide](docs/testcontainers.md) |
| Dual-generation guardrails | Compile-and-run matrices pinning Boot 3.5.16 / 4.0.3 plus version sentinels | [Boot 3 → 4 guide](docs/boot3-to-boot4.md) |
| Exception model | `MeiliOrmException` root; mapping errors fail fast; server error codes passed through | javadoc of `core.exception` |

### 🚫 Non-goals (explicitly out of scope)

Reactive support (the Meilisearch Java SDK is synchronous/blocking), `@Version` optimistic
locking, per-field type mapping / analyzers, nested relation queries, SpEL dynamic index names,
audit principals (`@CreatedBy`/`@LastModifiedBy`) and pluggable clocks, and connect/read timeout
properties (hard SDK constraint — see [limitations](docs/limitations.md)).

## ⚙️ Configuration

| Property | Default | Notes |
|---|---|---|
| `meili.enabled` | `true` | Master switch |
| `meili.url` | `http://localhost:7700` | Server URL |
| `meili.api-key` | (empty) | Master key or API key |
| `meili.wait-task` | `false` | Block writes until the task reaches a terminal state |
| `meili.wait-timeout` | `5s` | Per-task wait budget |
| `meili.client-agents` | `meili-orm` | Extra User-Agent tokens (`;` separated, appended after the SDK's own version token; empty falls back to the SDK default) |
| `meili.index.auto-init` | `create-if-missing` | `none` / `create-if-missing` / `sync-settings` |
| `meili.index.on-settings-drift` | `warn` | `warn` / `apply` / `fail` (only `sync-settings` ever writes) |
| `meili.repositories.enabled` | `true` | Scan/register repositories once the opt-in coordinate is present |

There are deliberately no `connect-timeout` / `socket-timeout` properties: the official SDK's
`Config` builds its own OkHttpClient with no injection point
([limitations](docs/limitations.md) item 1).

## 🎮 Demos

Two runnable examples under [`examples/`](examples/README.md) (Boot 3.5.16 and 4.0.3 shells sharing
one business codebase) cover import, full search (q + filter + sort + paging + facets), single read,
delete, callbacks and the raw escape hatch — one-command flow and live curl transcripts in the
[examples README](examples/README.md).

## 📁 Project Structure

A multi-module Maven reactor (root `pom.xml` `<modules>`). The GitHub repository is `Meili-ORM`;
Maven artifactIds, the Java package and the config prefix keep the lowercase `meili-orm` stem.
Six artifacts are published; everything else is build-only or tooling.

```text
Meili-ORM/                                # repo root · Maven reactor (coordinates/prefix keep lowercase meili-orm)
├── meili-orm-core/                       # published — mapping, query IR, settings, Operations (no Spring)
├── meili-orm-spring-boot-autoconfigure/  # published — Boot auto-config, index init & settings sync
├── spring-boot-starter-meili-orm/        # published — core + autoconfigure + Boot base starter
├── meili-orm-serializer-jackson3/        # published (opt-in) — Jackson 3 serializer for Boot 4
├── meili-orm-repository/                 # published (opt-in) — declarative repositories
├── meili-orm-testcontainers/             # published (opt-in) — typed container + service-connection
├── it/                                   # build-only — integration-test matrix (not the pronoun)
│   ├── meili-orm-it-boot3/               # pinned Spring Boot 3.5.x run
│   ├── meili-orm-it-boot4/               # pinned Spring Boot 4.x run
│   └── meili-orm-it-boot4-jackson3/      # Boot 4 + Jackson 3 module run
├── examples/                             # build-only — dual-generation runnable demos
│   ├── meili-orm-example-common/         # shared Book/Author code + REST controller
│   ├── meili-orm-example-boot3/          # Boot 3.5.x launcher shell
│   └── meili-orm-example-boot4/          # Boot 4.x launcher shell
├── docs/                                 # English guides + zh-CN/ mirrors + internal/ records
│   ├── *.md                              # mapping-guide · limitations · boot3-to-boot4 · testcontainers
│   ├── zh-CN/                            # Chinese mirrors
│   └── internal/                         # non-deliverable design records
├── openspec/                             # spec-driven change management
│   ├── specs/                            # current capability specs
│   └── changes/                          # in-flight + archived change records
├── scripts/                              # build-gate helpers (check-source-citations.sh)
├── ci/                                   # CI-only Maven settings.xml
├── etc/                                  # build resources — license-header.txt (license gate)
├── .github/                              # GitHub Actions
│   └── workflows/verify.yml              # CI workflow
└── .mvn/                                 # Maven CLI defaults (maven.config)
```

## 🧪 Build & Test

```bash
mvn clean verify
```

Prerequisites: a running Docker daemon and the `getmeili/meilisearch:v1.49.0` image locally —
core/autoconfigure/matrix integration tests run against a real server via Testcontainers (without
Docker the ITs fail fast with a recognizable error). The full build also enforces three gates:
private-member Javadoc completeness, an internal-citation scanner, and Apache-2.0 license headers
on every Java file. Maintainers' machine-specific Maven settings (mirrors/local repo layout) are
described in [CONTRIBUTING](CONTRIBUTING.md).

## 📚 Documentation

- [Mapping guide](docs/mapping-guide.md) — annotations → Meilisearch concepts/settings, projection pipeline, callbacks, derived queries
- [Limitations](docs/limitations.md) — 18 evidenced behavioral boundaries, each with a workaround
- [Boot 3 → 4 upgrade guide](docs/boot3-to-boot4.md) — dual-generation compatibility strategy and the Jackson 3 module
- [Testcontainers integration](docs/testcontainers.md) — typed container, `@ServiceConnection`, manual bridge
- [Examples](examples/README.md) — runnable demos with live transcripts
- Chinese documentation: [README.zh-CN.md](README.zh-CN.md) · [docs/zh-CN/](docs/zh-CN/)

## 🤝 Contributing

Build commands, the three build gates and the source-language/licensing conventions are documented
in [CONTRIBUTING.md](CONTRIBUTING.md).

## ⚖️ License

[Apache License 2.0](LICENSE). Built on the official
[meilisearch-java](https://github.com/meilisearch/meilisearch-java) SDK.
