[中文](zh-CN/boot3-to-boot4.md)

# Upgrading from Spring Boot 3 to 4 (meili-orm perspective)

## Conclusion first

The same `spring-boot-starter-meili-orm` jar runs on both Boot 3.5.x and 4.x: upgrading an
application from 3.5 to 4.x requires **no coordinate change and no configuration change on
the meili-orm side** (the `meili.*` property prefix and the `AutoConfiguration.imports`
mechanism are identical across both generations). The only decision point is whether to add
the Jackson 3 serialization module.

## How dual-generation compatibility works

- **Zero Spring dependencies in core**: annotations, metamodel, serialization abstractions,
  query IR, and Operations all live in a pure-Java layer that never touches any
  generation-specific API;
- **autoconfigure references only the assembly base that is stable across both generations**
  (`@ConfigurationProperties`, `@ConditionalOn*`, `ObjectProvider`, the imports mechanism);
  the compile baseline is the lowest supported generation, Boot 3.5.16, with a bytecode
  baseline of Java 17;
- **Serialization-generation isolation**: the container ObjectMapper on Boot 4 is Jackson 3
  (`tools.jackson`), while the main module's default serializer is built on Jackson 2 (it
  constructs its own instance when no container bean is found — behavior in item 4 of the
  [limitations list](limitations.md)); Jackson 3 semantics are taken over by the optional
  module;
- **The guardrails are compile facts**: `it/meili-orm-it-boot3` (pinned 3.5.16) and
  `it/meili-orm-it-boot4` (pinned 4.0.3) each hold a byte-identical copy of the starter IT
  (differing only in version-sentinel expectations), plus `it/meili-orm-it-boot4-jackson3`
  (opt-in takeover verification), and each compiles and runs against a real container
  independently within the root `mvn clean verify`. A mistakenly referenced
  single-generation API or pinned-version BOM drift turns the matrix red on the spot —
  every matrix module carries a `SpringBootVersion` sentinel assertion proving it really
  runs on the generation it is pinned to.

### One intentional exception to the compile baseline

`meili-orm-serializer-jackson3` is the only product module in the project compiled against
the Boot 4.0.3 BOM as its compile baseline — the `tools.jackson` coordinates are managed
only by that generation's BOM. This is not a discipline breach: a Boot 3 application has no
`tools.jackson` on its classpath, and the module's conditional assembly (class presence,
evaluated via bytecode metadata without triggering class loading) makes it completely inert
on Boot 3; the it-boot3 and baseline it-boot4 modules in the matrix continuously prove
that an absent class means no takeover.

## Behavior differences after upgrading to Boot 4

| Aspect | Boot 3.5.x | Boot 4.x |
|---|---|---|
| Default serializer | Jackson 2 (prefers the container `ObjectMapper` bean) | Jackson 2 self-constructed instance (the container's Jackson 3 mapper is not used) |
| Want Jackson 3 semantics | N/A | Add the `meili-orm-serializer-jackson3` dependency to take over; or register your own `MeiliDocumentSerializer` bean |
| Web-layer serialization | Jackson 2 | Jackson 3 (independent of the meili-orm read/write channel — the entity-document channel always goes through `MeiliDocumentSerializer`) |

Jackson3 module usage:

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>meili-orm-serializer-jackson3</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

After takeover, behavior is equivalent to the Jackson 2 implementation item by item (rename
bridging, Long precision, ISO dates, unknown-key leniency — verified by a resident mirror
assertion suite); if the container already defines a Jackson 3 `ObjectMapper` bean, that
bean is used as the base configuration.

## The repository layer and spring-data-commons

`meili-orm-repository` is the only product module that depends on `spring-data-commons`.
Its commons version resolves through the Boot 3.5.16 BOM (the lowest-supported-generation
compile baseline), so the same jar runs against whichever commons generation the hosting
Boot supplies at runtime — there are no boot3/boot4 variants of the module. Runtime
compatibility across both generations is proven in the dual-generation matrix: byte-identical
repository ITs run on each pinned generation, accompanied by a commons structural sentinel
asserting that a 4.0-only class (such as `org.springframework.data.core.PropertyPath`) is
present or absent exactly as expected for the generation under test, so drift on the commons
surface the repository layer relies on turns the matrix red.

## For maintainers

Maintainer-side dependency-upgrade duties (changing the pinned versions above and the
verification each change requires) live in [CONTRIBUTING.md](../CONTRIBUTING.md).
