# Design

## Context

M0–M3 已收口：core（零 Spring 元模型/序列化/`MeiliQuery` IR/Operations/raw 通道）、autoconfigure（三 AC 链）、双代矩阵与可选 jackson3 模块全部常驻绿。本 change 在其上加 Repository 层，受三条既有硬约束塑形：

1. **依赖纪律**（设计基线 §3.2）：`spring-data-commons` 只允许进入 repository 模块；core 零 Spring 不可动摇。
2. **编译基线取最低代**：本仓库产品模块统一按 Boot 3.5.16 侧编译，Boot 4.0.3 由矩阵运行期验证。落到 commons 上即"编译钉 3.5.13、运行期两代矩阵"。环境事实：本机 Maven 仓库仅有 commons 4.0.3，3.5.13 需联网首拉。
3. **raw 通道契约**（spikeA 结论）：数据读写只走 `MeiliSearchOperations`，Repository 层不触碰 SDK。

核心张力：commons 的查询解析设施（`PartTree`/`PropertyPath`）围绕 **commons 自己的持久类模型**构建，而字段名权威在 **core 元模型**（`@MeiliField.name` 改名、`author.city` 点路径、Jackson 拥有字段排除语义）。两个属性模型必须交接，这是本设计最大的一块决策，排在 4.1 spike 首位。

## Goals / Non-Goals

**Goals:**
- Spring Data 直觉的 `MeiliRepository`：CRUD + 派生查询 + `@MeiliQuery`，双代运行等价。
- 错误前移：派生查询与注解模板的全部可静态判定错误在应用启动期暴露。
- 隔离从纪律变成构建事实（enforcer + 矩阵）。

**Non-Goals:**
- DTO 投影、`Stream` 返回、异步仓库、`@Async`、审计（`@CreatedDate` 等，设计基线归 Repository 之后再评估）、Testcontainers `@ServiceConnection` 接入。
- 修改 core/autoconfigure/starter 任何公开 API 或依赖树。
- 响应式（永久非目标）。

## Decisions

### D-1 自动配置位置：repository 模块自注册（偏离设计文档 §5.1，此处裁决）

设计文档把 `MeiliRepositoriesAutoConfiguration` 列在 autoconfigure 模块结构内。**否决**：该 AC 必然引用 `MeiliRepositoryFactoryBean`/Registrar（repository 模块类），放 autoconfigure 会制造 autoconfigure→repository 编译依赖，starter 聚合后 commons 泄漏给全体用户，§3.2.3 隔离前提被击穿。采用 `meili-orm-serializer-jackson3` 已验证的先例：repository 模块自带 `AutoConfiguration.imports`，条件链含 `@ConditionalOnBean(MeiliSearchOperations.class)`（数据层缺席时整体静默退避）。**starter 不聚合 repository**：能力 opt-in，`meili.repositories.enabled` 与元数据归本模块。

替代方案（考虑后弃）：AC 放 autoconfigure 并用字符串 `afterName`+反射装配——编译依赖消除但运行期脆弱，且 imports 文件仍在 autoconfigure 制品中声明 repository 类，违反"注册文件完整性"现有能力的可加载语义。

### D-2 方法名解析：spike 已定案为"自研语法 + core 语义桥"（路线 a 证伪，2026-09-26 实证回写）

原候选与终局：

| 路线 | 裁决 |
|---|---|
| (a) commons PartTree 解析 + PropertyPath 桥回 core | ❌ **spike 证伪**：`Part.getProperty()` 返回类型代际迁移（3.5 `mapping.PropertyPath` → 4.0 `core.PropertyPath`，旧类在 4.0 jar 中整体不存在），方法描述符入字节码即代际绑定；`RepositoryFactorySupport`/`BeanSupport` 的受保护钩子（`getTargetRepositoryViaReflection`、`getQueryLookupStrategy(…, QueryMethodEvaluationContextProvider)`、`isSingleton` 等）在 4.0 删改，继承基类同样单源码不可双代。 |
| **(b) 自研语法解析 + 反射字典属性切分 + core 投影名桥** | ✅ **采用**。commons 仅保留零漂移面：`org.springframework.data.domain.*`（Sort/Pageable/PageImpl）、`RepositoryConfigurationExtensionSupport`（@Enable 侧）、`EntityInformation`。26 条黄金断言在 3.5.13 编译基线与 4.0.3 运行期双代全绿（证据：`evidence/spike-1.4-commons-bridge.md`）。代价 = 属性缩写不支持（显式报错），本属 v1 支持子集外。 |
| (c) core 元模型改建到 commons 底座 | ❌ 否决（不变）：违背 core 零 Spring。 |

工厂形态随之定型：**不继承** `RepositoryFactorySupport`/`RepositoryFactoryBeanSupport`，以 `FactoryBean + InvocationHandler` 自持代理（方法解析表在构造期一次性建立）。薄变体预案**不启用**——spike 判据 ②③ 均满足。

### D-3 CRUD 语义三条裁决（commons 接口逼出来的必答项）

- `findAll()`：**实现**（documents/fetch 通道），到 `maxTotalHits`（默认 1000）截断 + WARN，不抛 `UnsupportedOperationException`——Spring Data 用户会直接调用，静默截断+日志比抛异常更符合最小惊讶，且截断义务已入 spec 与 limitations。
- `Page.getTotalElements()`：取 `estimatedTotalHits`，估算语义入 spec（禁止伪装精确）。
- `deleteAll(Iterable)`：**v1 逐条单文档删除，零 core 改动**。备选"给 `MeiliRawGateway` 加批量删方法"被否：收益（N→1 请求）不值为本里程碑撕 core 内部面改动的口子；代价与批量化路径记入限制文档，作为后续 change 的候选。

### D-4 启动期角色预检：相对 SDE 的增强，判定源唯一

Meili 的 filter 打在未声明 filterable 的属性上是**运行期 400**（ES 无此坑），因此把"实体角色声明"作为派生查询的静态可判定输入：filter 路径→`filterable`、sort 路径→`sortable`、Containing→`searchable`，缺失即启动失败并给两条修复指引。预检输入**只看实体声明、不看服务端实际 settings**（含 `@MeiliSetting` 透传）——否则校验对象随远端状态漂移，启动结果不可复现；透传声明角色而实体未声明的场景按 spec 判启动失败+消息提示，保证"实体即契约"单一真相。

### D-5 `distinct` 归属注解面

`findDistinct…` 修饰符在 commons 解析后**不携带属性名**，而 Meili `distinct` 必须指定属性——派生面无信息源，强行支持必造假。裁决：派生关键字报不支持（D-4 同族），`@MeiliQuery(distinct = "…")` 提供属性来源。

### D-6 filter 模板参数渲染：渲染器负责引号

`filter` 模板中的 String 参数值由渲染器**自动包裹字符串字面量并转义 `"`/`\``**，模板作者不写引号（`"genre = :g"` 而非 `"genre = \":g\""`）。备选"原样插值（SDE 式 SpEL 行为）"被否：Meili filter 是纯文本 DSL，原样插值把注入面直接暴露给参数值，而本库用户经 REST 透传参数的比例高。数值/布尔裸字面量、`q` 模板不转义（全文非 DSL）。转义器与派生查询共用一个实现，L1 注入用例集专门打透。

### D-7 构建护栏落点

`bannedDependencies(org.springframework.data:*)` 挂在 core/autoconfigure/starter 三模块 pom（enforcer 已在 Maven 核心插件族，无版本漂移风险）；repository/it/examples 不挂。该护栏同时是"未引入模块零扰动"spec 场景的机器可证形态。

## Risks / Trade-offs

- [commons 3.5→4.0 代差 API 漂移] → spike 已证并用：实现面收敛到双代零漂移清单（D-2）；矩阵 Repository IT 双代常驻哨兵，后续若扩面再犯漂移在全量构建即暴露。
- [两套属性模型桥接出静默不一致（commons 解析成功但 core 查无投影名）] → 查不到一律启动期异常（spec 锁死），不存在运行期回退路径。
- [Containing→q 近似语义与方法名字面直觉偏差（全文分词命中，非子串）] → 映射表+限制文档双写；真机 IT 钉行为；文档给出 `attributesToSearchOn` 缓解。
- [maxTotalHits/估算总数触发用户误用] → WARN 日志、limitations 条目、README 样例直接用分页而非 findAll。
- [逐条批量删在大数据量下性能差] → v1 明示；批量化列为后续 change 候选（附 D-3 理由）。
- [commons 3.5.13 联网首拉失败] → 阻断项：按纪律停下报告，不改默认 settings、不偷升基线。
- [新模块受 javadoc/citation 双门禁（src/main 禁现 "M4" 等内部引用）] → 契约只读自洽撰写；提交前 `check-source-citations.sh --selftest` + 全量扫描自查为任务项。

## Migration Plan

纯增量：新模块入 reactor，无存量 API/装配变更，无数据迁移。回滚 = 从根 pom 摘除模块 + 依赖方删坐标。用户升级路径：加一行 `meili-orm-repository` 依赖即得能力，不加则字节码/依赖树与升级前逐位相同。

## Open Questions

- Like 通配符（`?`/`*`）是否有真实需求：v1 按 Containing 等价，观察发布反馈，需要时映射到 BEGINS WITH 等 DSL——不改变本 change 任何面。
- 批量删（网关 `deleteDocuments(JSON 数组)`）的批量化 change 时机——独立演进。
