# Contributing to meili-orm

[中文](CONTRIBUTING.zh-CN.md)

## Prerequisites

- **JDK 25** to build (bytecode baseline is Java 17 via `maven.compiler.release=17` — the
  published artifacts must stay 17-compatible).
- **Maven 3.9+**. If your environment needs a custom settings file (mirrors, local repository
  path), pass it explicitly: `mvn -s /path/to/your/settings.xml ...`. On the project's primary
  development machine this is `-s /home/lam/repo/settings.xml`; CI instead uses the committed,
  path-free [`ci/settings.xml`](ci/settings.xml).
- **Docker** daemon plus the `getmeili/meilisearch:v1.49.0` image locally — integration tests run
  against a real Meilisearch server via Testcontainers. Testcontainers also needs its `ryuk`
  sidecar image; pre-pull it (or disable ryuk in `~/.testcontainers.properties`) if your network
  cannot reach Docker Hub.

## Build

```bash
mvn clean verify
```

This compiles every module (product modules, the Boot 3.5.16 / 4.0.3 / Jackson3 matrices, the
examples), runs unit and integration tests, and enforces the three gates below. There are no
partial escape hatches: the matrices and sentinel ITs are the compatibility contract.

The reactor layout: five product modules (`meili-orm-core`, `meili-orm-spring-boot-autoconfigure`,
`spring-boot-starter-meili-orm`, `meili-orm-serializer-jackson3`, `meili-orm-repository`, plus the
opt-in `meili-orm-testcontainers`), `it/` compatibility matrices, and `examples/` demo apps. The
repository and testcontainers modules are deliberately **not** aggregated by the starter.

## Build gates

| Gate | What it enforces | How to satisfy |
|---|---|---|
| **Javadoc completeness** | `maven-javadoc-plugin` with `show=private` + `failOnWarnings`: every type/method/field (including private) in `src/main` must carry Javadoc | Write the Javadoc; depth should match the contract's weight (simple accessors stay brief) |
| **Internal-citation scanner** | [`scripts/check-source-citations.sh`](scripts/check-source-citations.sh): published-module `src/main` comments and pom `<description>` must not reference internal process material (design-doc section numbers, milestone/task codes, openspec change names) | Describe behavior self-containedly; run the script (and `--selftest`) before committing |
| **License headers** | `com.mycila:license-maven-plugin` `check` bound to `validate`: every `**/*.java` under `src/main`/`src/test` must carry the exact Apache-2.0 header from [`etc/license-header.txt`](etc/license-header.txt) (copyright line `Copyright 2026 the original author or authors.`) | `mvn com.mycila:license-maven-plugin:format` inserts headers automatically |

## Source language convention

All comment and human-facing text in the repository is **English**: Java comments and Javadoc
(main and test, including trailing end-of-line comments), pom XML comments and `<description>`,
example `application.yml` comments, and `src/main` runtime text (exception messages, log output).
Chinese string literals that carry *test/demo data* semantics (CJK round-trip precision samples,
the demo book catalog, golden resources) are deliberate and stay; exemptions must be an explicit
enumeration in the change's evidence, never an implicit exception. Message text is not an
assertion contract — translate messages and their test assertions together.

## Maintainer upgrade checklist

The project's dual-generation and precision guarantees hold only while the matrices keep running.
After changing **any** pinned version below, the corresponding verification is mandatory; any
failure is a blocking defect (do not rescue a green build by swapping one side's dependency tree).

### A. Bumping the Meilisearch Java SDK (`<meilisearch-java.version>` in the root pom)

```bash
mvn clean verify    # full reactor: core ITs, autoconfigure ITs, both matrices
```

1. Re-read the sentinel conclusions: the spike ITs lock "custom JsonHandler is incompatible with
   the SDK's internal typed models" and "raw → Jackson is lossless for `Long` primary keys". A red
   sentinel means SDK behavior changed — revisit the read/write-channel decision and update the
   spike evidence archive; never bend assertions to fit.
2. Run one demo round (`examples/README.md`) against a real server.

### B. Bumping a supported Boot generation (root `<spring-boot.version>` or the BOM pins in
`it/*`, `examples/*`)

```bash
mvn clean verify
# pin cross-check: each matrix module must resolve exactly one Boot version
mvn dependency:tree -pl it/meili-orm-it-boot3 | grep org.springframework.boot | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | sort -u
mvn dependency:tree -pl it/meili-orm-it-boot4 | grep org.springframework.boot | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | sort -u
```

If a newer generation moves or removes the autoconfiguration substrate this project relies on
(`@ConfigurationProperties`, `@ConditionalOn*`, `ObjectProvider`, the imports mechanism), that is
a compatibility-boundary event and needs a design review, not a patch.

### C. Bumping the pinned Meilisearch server image (root `<meili-server.version>` and the
`MeiliContainer` image constant)

```bash
docker pull getmeili/meilisearch:v1.50.0   # replace with the target version
mvn clean verify
```

1. One demo round; pay attention to settings projection keys, `documents/fetch`, stats counting
   and filter syntax (the facts behind limitations items 2, 6, 8).
2. Update the pinned-version statements in README (both languages) and `docs/limitations.md`.

### D. Bumping build-surface dependencies (Testcontainers, plugins, demo deps)

`mvn clean verify` suffices (the matrices and demo smoke tests run within the reactor). TC pins
live in `it/pom.xml` (`<testcontainers.version>`) — the Boot 4 BOM no longer manages TC.

### E. First release to Maven Central (once published)

Publish coordinates, add the Maven Central version badge to `README.md` / `README.zh-CN.md`
(the install section currently states the not-yet-published truth and must be rewritten), and
record the release in `CHANGELOG.md`.

## Notes

- `docs/internal/` holds non-deliverable process materials (design documents, the original
  implementation plan, spike evidence, an upstream issue draft). Deliverable documentation must not
  presume them; they live on for repository history.
- GitHub repository *About* metadata (description/topics) is maintained in the web UI; the current
  text is recorded in `openspec/` change `m8-docs-bilingual-restructure` design §D6.
