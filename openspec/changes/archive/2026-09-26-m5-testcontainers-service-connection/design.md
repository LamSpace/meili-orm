# Design

## Context

动机见 proposal.md - Why。塑造方案的事实（本变更创建时实证）：

1. SPI 双代坐标**不同名**：Boot 3.5.16 的服务连接工厂 SPI 为 `org.springframework.boot.testcontainers.service.connection.ConnectionDetailsFactory`（独立 `spring-boot-testcontainers` 构件）；Boot 4.0.3 中该 FQCN 不存在，同名接口位于 `org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory`（并入 autoconfigure 构件），且 4.0.3 的 testcontainers 支持构件在本地仓库无登记。
2. 既有 `MeiliConnectionDetails` bean back-off 契约已入主 spec（用户声明即替换属性实现）——**桥接是语法糖，降级为手工样例不破坏任何既有承诺**。
3. 本地仓库无 Boot 4 testcontainers 构件，定位其形态需联网首拉（it-boot3 首拉先例可援引）。

## Goals / Non-Goals

**Goals:**
- 类型化容器（可独立于服务连接机制使用）。
- `@ServiceConnection` 一行注解接入；形态经 spike 三选一定案后唯一化。

**Non-Goals:**
- 不进入 starter 聚合必选项；不提供非 MeiliSearch 容器；不重造 Testcontainers JUnit5 集成（用户用标准注解）；不实现 Boot 4 若其支持面未备——降级路径见 D1。

## Decisions

### D1 桥接形态 spike 前置，三选一
① 定位 Boot 4.0.x 服务连接支持（构件坐标、`@ServiceConnection` 注解与工厂发现机制的 FQCN 与时序）；② PoC 测试工厂发现机制对"类路径上存在引用缺失接口的工厂条目"是否容错（`NoClassDefFoundError` 行为）；③ 判定：(b) 单模块双包实现按代生效可行 → 单模块；不可行但双代机制均确认 → (a) boot3/boot4 双薄变体（镜像 commons 应急先例，模块内子目录即可，不出独立 artifact 除非必要）；4.x 支持面未备或成本失控 → (c) 降级：只交付容器 + README 手工桥接样例，**并在 apply 中同步裁掉本 delta 的桥接 requirement 与 proposal 对应条目**。

#### D1-Result 实证定案（任务组 1 回写，2026-09-26）

**① Boot 4.0.x 支持面：具备。** `spring-boot-testcontainers:4.0.3` 由 Boot 4.0.3 BOM 管理（本地仓库无，联网首拉成功）。双代支持面 FQCN 清单（javap 逐类比对，签名级一致）：

| 面 | FQCN @ 构件（两代同） | 备注 |
|---|---|---|
| `@ServiceConnection` 注解 | `org.springframework.boot.testcontainers.service.connection.ServiceConnection` @ `spring-boot-testcontainers` | 属性 `value()/name()/type()` 双代一致 |
| 工厂 SPI 接口 | `org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory<S,D>` @ `spring-boot-autoconfigure` | `D getConnectionDetails(S)` 双代一致 |
| 容器工厂基类 | `org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory<C,D>` @ `spring-boot-testcontainers` | 构造器/抽象方法 `getContainerConnectionDetails(ContainerConnectionSource<C>)`/`ANY_CONNECTION_NAME` 双代一致 |
| 容器连接源 | `org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource<C>` @ `spring-boot-testcontainers` | public 面一致 |
| 发现注册 | `META-INF/spring.factories`，key=工厂 SPI 接口 FQCN | 3.5.16 由 spring-boot-testcontainers 自带条目自证；4.0.3 由 spring-boot-jdbc/elasticsearch/flyway/data-redis/micrometer-metrics 等模块同 key 登记实证 |
| 时序链 | `ServiceConnectionContextCustomizerFactory`（`ContextCustomizerFactory` spring.factories 登记）→ `ServiceConnectionContextCustomizer` → `ConnectionDetailsRegistrar` → `ConnectionDetailsFactories`（SpringFactoriesLoader 装载全量工厂） | 链上各类双代 FQCN 一致，容器启动发生在 customizer 阶段（refresh 前） |

**前提修正（含二次实证修正）**：proposal/Context 所记"3.5.16 SPI 为 `org.springframework.boot.testcontainers.service.connection.ConnectionDetailsFactory`、双代不同名"不成立——GA 构件 3.1.12/3.2.12/3.3.5/3.4.13/3.5.16/4.0.3 的 `spring.factories` 发现 key 与工厂基类 javap 实测均指向 `org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory`（旧名仅在 3.1 milestone 期短暂存在）。服务连接工厂 SPI 自 3.1 GA 起双代乃至全代同名同签名，"单实现双兼容不可行"前提不成立。

**② 工厂发现容错 PoC：双代均容错。** 最小类路径实测（`ConnectionDetailsFactories` 直接构造，绕开全上下文）：spring.factories 注册 broken 条目（实现当代 SPI、另引用运行时缺失的拟旧代接口）置于队首、其后跟 valid 条目：

- Boot 3.5.16（autoconfigure 3.5.16 + spring-core 6.2.19 + spring-jcl）：不抛异常，valid 正常发现（size=1），broken 被静默跳过；补上缺失接口后同一条目被发现（size=2）——排除"因其他原因被过滤"。
- Boot 4.0.3（autoconfigure 4.0.3 + spring-core 7.0.5 + commons-logging 1.3.5）：行为完全一致（1 → 跳过 → 2）。

**③ 判定：(b′) 单模块单包。** 双代 FQCN 同构使 (b) 的"双包按代生效"退化为**单一工厂实现双代同时生效**：无需双包切分，无需 (a) 双薄变体；②的容错实测为类加载边界提供额外保险（异代残链条目只会被跳过、不会杀上下文）。(c) 未触发，delta spec"服务连接桥接"requirement 与 proposal 桥接条目维持。依赖钉法随之简化：编译基线 `spring-boot-testcontainers` + `spring-boot-autoconfigure` 取 3.5.16 代（Boot BOM 对齐），TC 经 3.5.16 BOM 管理为 1.21.4（与 it 矩阵钉版同源）；4.0.3 运行代经上述签名一致性直接复用同一份字节码。

### D2 容器设计
`MeiliSearchContainer extends GenericContainer<MeiliSearchContainer>`：镜像常量 v1.49.0、`withExposedPorts(7700)`、`MEILI_MASTER_KEY` 环境注入（默认测试密钥可配）、`Wait.forHttp("/health")`；`getUrl()`/`getApiKey()` 供桥接。不继承任何上游模块类型（上游无 MeiliSearch 模块，避免虚构依赖）。

### D3 桥接产物只有 `MeiliConnectionDetails`
不产 Client、不产 operations——下游自动配置链完全复用，行为面最小。

#### D3-Result 实现定案（任务组 2 回写，2026-09-26）

实现暴露两处机制缺口，均已定案落地：

1. **泛型界**：Boot 的 `ContainerConnectionDetailsFactory<C, D extends ConnectionDetails>` 要求 `D` 继承 Boot 标记接口，而原 `MeiliConnectionDetails` 未继承（编译实证）。拍板：seam 加 1 行 `extends org.springframework.boot.autoconfigure.service.connection.ConnectionDetails`（用户选择；备选"新模块内交叉子接口"被否）。行为零变化：标记接口无方法、源/二进制兼容；且 Boot 自家属性默认按子接口条件退避（javap `@ConditionalOnMissingBean(JdbcConnectionDetails.class)` 实证），meili 的 details bean 不会误伤其他服务默认值。proposal Impact 已同步。
2. **用户 bean 退避机制**：Boot 注册器的"已存在则跳过"检查发生在测试上下文 customize 阶段——早于用户 `@Bean` 定义解析（`ServiceConnectionContextCustomizer.customizeContext` → `ConnectionDetailsRegistrar`，javap 实证），看不到用户自有 bean；桥接 bean 与用户 bean 共存将使 `MeiliConnectionDetails` 按类型注入歧义，不满足 spec"用户连接详情 bean 优先"。新模块内补 `MeiliServiceConnectionBackoffConfiguration`（`@AutoConfiguration` + static `BeanFactoryPostProcessor`）：全量配置解析后、实例化前，凡带注册器标记（bean 定义属性 key=`ServiceConnection` FQCN，双代 javap 一致实证）的 details 定义，只要上下文同时存在无此标记的 `MeiliConnectionDetails` 定义即被移除——桥接退避、用户 bean 独占，幂等（无标记即 no-op）。starter 聚合面与既有模块行为均不变，退避逻辑单点落在新模块。

### D4 依赖与纪律
编译依赖 testcontainers core +（按 D1 结果）对应 Boot 代际的支持构件；`spring-data-commons` 零引入；版本经 Boot BOM 对齐（编译基线 3.5.16 代）；Javadoc/引用门禁按产品模块全量生效；`deploy` 参与发布面（与 it/examples 不同，需 pom `<description>` 合规）。

## Risks / Trade-offs

- [Boot 4 支持面未知，D1 结果直接改变交付范围] → spike 为任务组 1 且先于一切实现；(c) 路径有明确降级定义与 spec 同步义务，不会造成"artifact 与实现背离"。
- [工厂发现机制对破损类引用不容错杀死 (b)] → 正是 D1② 的测量对象；不容错即走 (a)，成本上界 +0.5d。
- [CI/网络首拉 4.x 构件失败] → 停下报告（it-boot3 先例），不私自改钉版。
- [容器单例 vs 上下文缓存：真机 IT 耗时] → 复用 `@ServiceConnection` 静态字段 + Testcontainers 复用机制惯例，样例即写法。

## Migration Plan

纯新增模块。回滚 = 移出 `<modules>`。未引入模块的既有用户零影响。
