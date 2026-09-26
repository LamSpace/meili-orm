# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
once released.

[中文](CHANGELOG.zh-CN.md)

## [Unreleased]

### Added

- Apache-2.0 license headers on every Java source file, enforced by a build-time gate
  (`license-maven-plugin:check` bound to `validate`; headers inserted via `license:format`).
- Open-source repository metadata in the root pom (`licenses`, `scm`, `url`, `developers`).
- Bilingual documentation set: English-canonical `README.md` with the `README.zh-CN.md`
  mirror, all guides under `docs/` in English with `docs/zh-CN/` mirrors, per-document
  language switch links.
- `CONTRIBUTING.md` (build commands, the three build gates, source-language convention,
  maintainer dependency-upgrade checklist) and this `CHANGELOG.md`, both bilingual.
- Badges: license, CI status, Java baseline, Spring Boot generations, Meilisearch server
  generation.

### Changed

- Exception messages and log output in `src/main`, and all source comments/Javadoc across
  main, test and examples, are now English (Chinese string literals that carry test/demo
  *data* semantics are preserved; see `CONTRIBUTING.md`).
- README install section now states the publishing truth explicitly: artifacts are not yet
  on Maven Central; install from source. Message wording is not part of the API contract.

### Removed

- Internal process materials (design document, original implementation plan, spike evidence
  archive, upstream issue draft) moved out of the deliverable surface into `docs/internal/`;
  deliverable docs no longer presume them.

### Release checklist (when the first Central release lands)

Add the Maven Central version badge, rewrite the install sections of both READMEs, and
replace `[Unreleased]` with the released version and date.
