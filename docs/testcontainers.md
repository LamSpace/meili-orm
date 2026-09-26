[中文](zh-CN/testcontainers.md)

# Testcontainers integration (meili-orm-testcontainers)

## At a glance

`meili-orm-testcontainers` is an opt-in coordinate — **not part of the starter
aggregate** (see the [README](../README.md)); add it in `test` scope where you
need it. It gives you two things:

1. **`MeiliSearchContainer`**: a typed Meilisearch service container that is fully
   usable on its own, outside Spring;
2. **the `@ServiceConnection` bridge**: a one-line annotation on the container
   field, and the bridge automatically produces a `MeiliConnectionDetails` bean —
   the whole assembly chain (`Config → Client → Operations`) then points at the
   container with zero hand-written configuration.

The bridge contributes the connection-details bean and nothing else — no Client,
no Operations. Everything downstream runs on the existing auto-configuration and
back-off contract.

## Usage: one annotation

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>meili-orm-testcontainers</artifactId>
    <version><!-- same version as the other meili-orm coordinates --></version>
    <scope>test</scope>
</dependency>
```

```java
@SpringBootTest
class BookSearchIT {

    @ServiceConnection                       // org.springframework.boot.testcontainers.service.connection
    private static final MeiliSearchContainer MEILI = new MeiliSearchContainer();

    @Autowired
    private MeiliSearchOperations operations;

    @Test
    void roundTrip() {
        operations.save(new Book(1L, "三体"));
        assertThat(operations.findById(1L, Book.class)).isPresent();
    }
}
```

The test context declares no `meili.url` and no `meili.api-key`: the bridged
container is the only source of connection information. Setting
`meili.wait-task=true` as a test property is recommended (the write path then
waits synchronously for the enqueued task to reach a terminal state, so
assertions are race-free); the index itself is created at startup by the default
`auto-init=create-if-missing` behavior.

### Precedence (high → low)

| Source | Behavior |
|---|---|
| user's own `MeiliConnectionDetails` bean | the bridge backs off (property defaults back off with it); `Config` uses the user bean |
| `@ServiceConnection` bridge bean | property defaults back off; `Config` uses the container URL/key |
| `meili.url` / `meili.api-key` properties | the default implementation when neither source above exists |

The bridge's back-off in favor of a user bean is enforced by an
auto-configuration guard shipped in this module. Boot registers service
connections during test-context customization — before user `@Bean` methods are
parsed — so it cannot see user beans on its own. The guard runs after all bean
definitions (user and auto-configured alike) have been parsed and before any
instantiation, and removes the bridge bean definition in scenarios where a
`@ServiceConnection`-marked bridge coexists with a user-owned details bean in
the same context.

## Container contract

| Item | Default | Override |
|---|---|---|
| image | `getmeili/meilisearch:v1.49.0` (exact pin) | pass a `String` / `DockerImageName` to the constructor: any tag from the same repository works as-is; a relocated image must declare compatibility via `DockerImageName.parse(...).asCompatibleSubstituteFor("getmeili/meilisearch")` (standard Testcontainers convention) |
| port | 7700 (random host mapping) | — (standard TC API) |
| master key | `masterKey-test-123456` (disposable, test-only credential) | `withMasterKey(String)` |
| readiness | `GET /health` → 200 | — (replaceable through the standard `waitingFor` API) |
| reads | `getUrl()` (requires a running container) / `getApiKey()` (readable from construction time) / `getConfiguredImage()` (pure config read, triggers no image resolution) | — |

## Manual bridging (without the service-connection mechanism)

Any Boot generation — including ones without `@ServiceConnection` — can wire the
container directly as a `MeiliConnectionDetails` bean. This is exactly the
declaration path of the existing back-off contract:

```java
@SpringBootTest
class BookSearchIT {

    private static final MeiliSearchContainer MEILI = new MeiliSearchContainer();

    static {
        MEILI.start();
    }

    @TestConfiguration
    static class BridgeConfig {

        @Bean
        MeiliConnectionDetails meiliConnectionDetails() {
            return new MeiliConnectionDetails() {
                @Override
                public String getUrl() {
                    return MEILI.getUrl();
                }

                @Override
                public String getApiKey() {
                    return MEILI.getApiKey();
                }
            };
        }
    }
}
```

## Dual-generation support matrix (stated as verified)

The bridge ships as a **single module, single package**: the `@ServiceConnection`
annotation, the factory SPI
(`org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory`),
the container factory base class, and the `META-INF/spring.factories` discovery
registration are **FQCN- and signature-identical across Boot 3.5.x and 4.x** —
one set of bytecode serves both generations.

| Boot generation | `@ServiceConnection` bridge | Manual bridge / typed container |
|---|---|---|
| 4.0.x | ✓ supported; 3.5.16-baseline bytecode runs directly on 4.0.3 (proven by real Docker-backed integration tests on both generations in the CI matrix) | ✓ |
| 3.5.x | ✓ supported (the compile baseline; proven by this module's real Docker-backed integration tests) | ✓ |
| 3.1–3.4 | no commitment (outside this project's Boot support surface as declared in the [README](../README.md)). Note: the service-connection discovery key has carried the same name since 3.1 GA (measured on artifacts 3.1.12–3.4.13), so the mechanism is reachable, but cross-generation binary compatibility of the container base classes has not been verified generation by generation | ✓ |
| ≤3.0 / 2.x | ✗ no service-connection mechanism | ✓ |

For ≤3.0, wire the container with the manual-bridge sample from the previous
section — the behavior is equivalent.
