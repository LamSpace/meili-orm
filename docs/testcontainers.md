# Testcontainers 集成（meili-orm-testcontainers）

## 结论先行

`meili-orm-testcontainers` 是 opt-in 坐标（**不在 starter 聚合内**，测试作用域按需引入），
提供两件事：

1. **`MeiliSearchContainer`**：类型化 MeiliSearch 服务容器，可脱离 Spring 独立使用；
2. **`@ServiceConnection` 桥接**：容器字段加一行注解，桥接自动产出
   `MeiliConnectionDetails` bean，装配链（`Config → Client → Operations`）零手写配置指向容器。

桥接只产连接详情这一个 bean，不产 Client/Operations——下游完全走既有自动配置与
back-off 契约。

## 用法：一行注解

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>meili-orm-testcontainers</artifactId>
    <version><!-- 与其余 meili-orm 坐标同版 --></version>
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

上下文不写任何 `meili.url` / `meili.api-key`：连接信息唯一来源是被桥接的容器。
建议测试属性配 `meili.wait-task=true`（写路径同步等任务终态，断言无竞态）；
索引由默认 `auto-init=create-if-missing` 在启动期建立。

### 优先级（高 → 低）

| 来源 | 行为 |
|---|---|
| 用户自有 `MeiliConnectionDetails` bean | 桥接退避（连同属性默认），`Config` 用用户 bean |
| `@ServiceConnection` 桥接 bean | 属性默认退避，`Config` 用容器 URL/密钥 |
| `meili.url` / `meili.api-key` 属性 | 前两者都不存在时的默认实现 |

桥接对用户 bean 的退避由本模块内的自动配置守卫完成：Boot 的服务连接注册发生在
测试上下文定制阶段，早于用户 `@Bean` 方法解析，无法自发看见用户 bean；守卫在全部
bean 定义解析完成后、实例化之前，移除"带 `@ServiceConnection` 标记且上下文同时存在
用户自有 details bean"场景下的桥接 bean 定义。

## 容器契约

| 项 | 默认 | 覆盖方式 |
|---|---|---|
| 镜像 | `getmeili/meilisearch:v1.49.0`（精确钉版） | 构造器传 `String` / `DockerImageName`：同仓库任意 tag 直接可用；镜像搬家须 `DockerImageName.parse(...).asCompatibleSubstituteFor("getmeili/meilisearch")` 声明兼容（Testcontainers 通用约定） |
| 端口 | 7700（宿主随机映射） | —（标准 TC API） |
| master key | `masterKey-test-123456`（测试专用一次性凭据） | `withMasterKey(String)` |
| 就绪条件 | `GET /health` → 200 | —（标准 `waitingFor` API 可换） |
| 读取 | `getUrl()`（需运行态）/ `getApiKey()`（构造期即可读）/ `getConfiguredImage()`（纯配置读，不触发镜像解析） | — |

## 手工桥接（不依赖服务连接机制）

任何 Boot 版本（含无 `@ServiceConnection` 的代际）都可以把容器直接接成
`MeiliConnectionDetails` bean——这正是既有 back-off 契约的声明路径：

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

## 双代支持矩阵（如实标注）

桥接形态为**单模块单包**：`@ServiceConnection` 注解、工厂 SPI
（`org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory`）、
容器工厂基类与 `META-INF/spring.factories` 发现注册在 Boot 3.5.x 与 4.x 两代
**FQCN 与签名完全同构**，同一份字节码两代通吃。

| Boot 代际 | `@ServiceConnection` 桥接 | 手工桥接 / 类型化容器 |
|---|---|---|
| 4.0.x | ✓ 支持，3.5.16 基线字节码直接在 4.0.3 运行（矩阵双代真机 IT 实证） | ✓ |
| 3.5.x | ✓ 支持（编译基线；模块真机 IT 实证） | ✓ |
| 3.1–3.4 | 不承诺（超出本工程 Boot 支持面）。注：服务连接发现 key 自 3.1 GA 起即与新代同名（3.1.12–3.4.13 构件实测），机制层面可达，但容器基类跨代二进制兼容未逐代验证 | ✓ |
| ≤3.0 / 2.x | ✗ 无服务连接机制 | ✓ |

≤3.0 如需容器接入，用上一节手工桥接样例即可，行为等价。
