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

### D2 容器设计
`MeiliSearchContainer extends GenericContainer<MeiliSearchContainer>`：镜像常量 v1.49.0、`withExposedPorts(7700)`、`MEILI_MASTER_KEY` 环境注入（默认测试密钥可配）、`Wait.forHttp("/health")`；`getUrl()`/`getApiKey()` 供桥接。不继承任何上游模块类型（上游无 MeiliSearch 模块，避免虚构依赖）。

### D3 桥接产物只有 `MeiliConnectionDetails`
不产 Client、不产 operations——下游自动配置链完全复用，行为面最小。

### D4 依赖与纪律
编译依赖 testcontainers core +（按 D1 结果）对应 Boot 代际的支持构件；`spring-data-commons` 零引入；版本经 Boot BOM 对齐（编译基线 3.5.16 代）；Javadoc/引用门禁按产品模块全量生效；`deploy` 参与发布面（与 it/examples 不同，需 pom `<description>` 合规）。

## Risks / Trade-offs

- [Boot 4 支持面未知，D1 结果直接改变交付范围] → spike 为任务组 1 且先于一切实现；(c) 路径有明确降级定义与 spec 同步义务，不会造成"artifact 与实现背离"。
- [工厂发现机制对破损类引用不容错杀死 (b)] → 正是 D1② 的测量对象；不容错即走 (a)，成本上界 +0.5d。
- [CI/网络首拉 4.x 构件失败] → 停下报告（it-boot3 先例），不私自改钉版。
- [容器单例 vs 上下文缓存：真机 IT 耗时] → 复用 `@ServiceConnection` 静态字段 + Testcontainers 复用机制惯例，样例即写法。

## Migration Plan

纯新增模块。回滚 = 移出 `<modules>`。未引入模块的既有用户零影响。
