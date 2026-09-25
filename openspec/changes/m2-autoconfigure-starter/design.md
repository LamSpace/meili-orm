# Design

## Context

M1 已冻结 `meili-orm-core` 公开 API（映射元模型、raw 通道网关、Operations、投影、回调注册表均带测试落地）。M2 在其上装配 Spring 层，约束来自三处实证事实与两条仓库纪律：

- SDK `Config` 构造面确无 OkHttpClient 注入口，且注入自定义 `JsonHandler` 会破坏 SDK typed 读模型（spikeA，结论见 `docs/spikes.md`）——因此 `Client` 必须保持默认 `GsonJsonHandler`，实体读写走 raw 通道。
- `MeiliRawGateway` 的 taskUid 全链路为 `int`（M1 出口已定案）；实施计划文本中残留的 String 写法不作准。
- 网关已暴露 `indexExists/createIndex/updateSettings/getSettings/awaitTask(int,Duration)` 全集——IndexInitializer 所需的原始能力 core 已备齐，无需回改。
- 构建门禁：autoconfigure 模块 javadoc 临时豁免（`skip=true`）在首个主源码类落地时必须移除；src/main 注释受内部引用扫描约束。
- 编译基线 Boot 3.5.16（最低支持代），Boot 4.0.3 运行时验证归 M3 双矩阵。

## Goals / Non-Goals

**Goals:**
- 三级条件装配链（客户端 → 数据 → 索引初始化），每一环可被用户 bean 整体替换。
- 启动期索引生命周期达到设计文档 §4.4 管线语义（三模式 × 三漂移策略），且启动期异常边界可证（fail-fast）。
- "加依赖 + 3 行配置即用"在 Boot 4.0.3 真机冒烟下成立。

**Non-Goals:**
- 不改动 core 任何公开/内部 API（M1 测试套件零重跑压力）。
- 不做 `@ServiceConnection` 动态容器接入、不做 Repository 自动配置（M4）、不做 Jackson3 接管模块（M3）。
- 不在 M2 提供 Boot 4 运行时矩阵验证（除出口冒烟单次触碰）。

## Decisions

### D-A：core 零改动，序列化器自建路径用裸 ObjectMapper

计划文本引用过 `Jackson2DocumentSerializer::newBase` 工厂——实际 core 只有 `Jackson2DocumentSerializer(ObjectMapper base)` 构造器，且构造器内部已 copy 并注册稳定默认模块。**决定**：数据层无容器 ObjectMapper 时直接 `new Jackson2DocumentSerializer(new ObjectMapper())`，不为 convenience 回改 core。**备选**：core 加无参工厂——触发 M1 套件重跑与 API 审查义务，收益仅省一个构造参数，否决。

### D-B：IndexInitializer 独立自动配置类 + SmartInitializingSingleton

**决定**：初始化器不塞进数据层 AC，独立 `MeiliInitializationAutoConfiguration`（after 数据层），初始化器实现 `SmartInitializingSingleton`，实际逻辑收在公开的 `initialize()` 上。**理由**：(1) back-off 粒度独立——用户可只替换/禁用索引初始化而保留 operations；(2) `afterSingletonsInstantiated` 保证网关/operations 协作者全部就绪后才发生服务端交互，异常语义清晰（启动失败而非"bean 创建顺序地狱"）；(3) `initialize()` 公有使 Mockito 单测无需拉 Spring 生命周期。**备选**：`InitializingBean`/`@PostConstruct` 挂在数据层——与其它 bean 的创建顺序耦合，用户自定义 serializer 尚未注入完毕的窗口存在，否决。

### D-C：启动期 fail-fast 边界（已获用户确认）

**决定**：auto-init 非 `none` 时，初始化期间的任何服务端异常（不可达、鉴权失败）以 `MeiliIndexAccessException` 传播 → 应用启动失败；索引名冲突与 drift=fail 抛 `MeiliMappingException` → 同样启动失败。**理由**：对齐 Boot 对数据源不可达的语义；索引初始化本就是"部署期迁移"性质的一次性动作，静默降级会把 settings 漂移藏进运行期。**代价**：本地跑 demo 忘了起容器 → 启动失败（信息明确）；冒烟与矩阵测试必须真机或显式 `auto-init=none`。**备选**：WARN 后继续（不对称：drift=fail 会炸而不可达不炸）；新增 required 开关（v1 未承诺的配置面，否决）。

### D-D：装配线哨兵借 MeiliConfigCustomizer 观测构建前 Config（已获用户确认）

**决定**：客户端 L2 测试注册一个捕获型 customizer，断言构建前 `Config` 的 `hostUrl`/`apiKey` 透传值，并断言 `jsonHandler` 仍为 `GsonJsonHandler`。这同时解决计划中 "connectionDetailsBeanWins" 的观测难题。**理由**：Config 字段可读是 SDK 公开形状；测试代码引用 SDK 类型不违反"core 公开 API 零 SDK 类型"纪律（约束只封 core 主源码）。与真机哨兵 `SpikeAJsonHandlerIT` 互补：一个封服务端行为漂移，一个封装配代码漂移。**备选**：反射读 Client 内部——脆弱且依赖 SDK 私有字段，否决。

### D-E：回调收集以标记接口统一收口

**决定**：数据层 AC 以 `ObjectProvider<MeiliCallback>` 流式收集全部回调 bean 注册进 `MeiliEntityCallbacks`（其泛型实参解析与匹配语义已由 core 实现），空上下文走 `MeiliEntityCallbacks.none()` 或零注册路径。**理由**：四件套回调共享一个标记接口正是 core 设计意图；autoconfigure 不做二次分型。

### D-F：实体扫描包来源 = AutoConfigurationPackages，失败即空集合

**决定**：`MeiliEntityScanner` 用 `ClassPathScanningCandidateComponentProvider(false)` + `AnnotationTypeFilter(@MeiliDocument)`；包来源 `AutoConfigurationPackages`，取不到（纯 L2 上下文/非 Boot）→ 返回空 + DEBUG 日志。**理由**：L2 条件测试不应被扫描失败拖炸；真机覆盖面归 L3 IT 与 M3 矩阵。索引 diff 所需 settings 现值经网关 `getSettings` 取 JSON 文本、双侧 `readTree` 比较，仅投影非 null 键参与（`searchableAttributes` 序列敏感、其余数组集合语义）。

### D-G：元数据与 imports 的验证位置

**决定**：属性提示经 `spring-boot-configuration-processor`（optional 依赖）生成 + `additional-spring-configuration-metadata.json` 补枚举 hints；`MeiliStarterMetadataTest` 逐行 `Class.forName` imports、核对生成元数据在册——两者都放 autoconfigure 测试侧（starter 模块无源码，不宜建 test）。

## Risks / Trade-offs

- [Boot 3.5.16 编译期 API 在 4.0.3 搬家] → 只允许引用两代稳定底座（`@ConditionalOn*`/`@ConfigurationProperties`/`ObjectProvider`/`AutoConfigurationPackages`/`SmartInitializingSingleton`）；出口冒烟强制 Boot 4.0.3 真实启动提前探测；M3 双矩阵为最终护栏。
- [启动期服务端交互拉长应用启动时间（逐索引建/推/等待）] → 初始化仅在启动生命周期一次性执行，运行期零开销；等待超时受 `meili.wait-timeout` 约束；`auto-init=none` 一键豁免。
- [drift=apply 涉及 filterable/sortable 触发服务端全量重建，数据量大时任务长] → 默认策略为 warn 不动手；apply 路径必发重建代价 WARN；等待超时抛 `MeiliTaskTimeoutException` 不无限挂起。
- [本地仓库缺 configuration-processor / starter-test 的 3.5.16 构件] → 首跑需联网；失败即停并报告，不擅自改回默认 settings。
- [javadoc 豁免移除后新类成门禁第一靶] → 每个任务写码时同步写全 Javadoc（含私有成员），注释措辞只读自洽、不引用内部材料（计划号/spike 名/设计节号），提交前跑 `check-source-citations.sh`。

## Open Questions

（无——契约点 fail-fast、哨兵测试、真机冒烟均已由用户拍板；剩余细节属任务内可判事项。）
