# Design: meili-orm M1 · meili-orm-core

## Context

M0 已收口的实证事实构成本设计的输入（详见 `docs/spikes.md` 与 proposal.md 的 Why，不复述）：

- 读写通道契约：Client 保持默认 `GsonJsonHandler`；实体读写唯一通道 = raw JSON 字符串 ↔ 自有序列化器（spikeA/spikeB）。
- SDK 0.21.0 签名锚点（javap + 实跑双确认）：`TaskInfo.getTaskUid(): int`、`Index.getRawDocument(String): String`、`Index.rawSearch(SearchRequest): String`、`Index.waitForTask(int,int,int)`、`Client.getTask(int): Task`；`SearchRequest` 的 setter 全覆盖 M1 所需 IR 字段（含 `setVector(Double[])`/`setHybrid(Hybrid)`/`setDistinct`/`setLocales`）；`Config.getHostUrl()/getApiKey()` 为 public。
- SDK **未暴露**的端点：documents/count、POST /documents/fetch（`DocumentsQuery` 无 sort）、settings 的原始 JSON 读写。
- 工程门禁现状：core pom 的 javadoc `<skip>` 注释明言"首个真实主源码类落地时删除"；`check-source-citations.sh` 在册。

## Goals / Non-Goals

**Goals:**

- core 公开 API 在 M1 出口即为冻结候选：签名一次定稿（含 int taskUid 口径），M2–M4 只消费不重议。
- "实现可换、契约不动"的两处接缝质量：`MeiliDocumentSerializer`（M3 Jackson3 需可满足）与 `MeiliRawGateway`（SDK 类型唯一出入口）。
- 每项行为契约都有 L1 单测或 L3 真机 IT 对应（spec 场景 ↔ 测试一一反查）。

**Non-Goals:**

- 不做 `MeiliMappingContext` 之外的实体注册/扫描机制（M2 `MeiliEntityScanner` 承载；"多实体 indexName 冲突"校验归 M2 IndexInitializer）。
- 不做并发批量写、分批次（`addDocumentsInBatches` 类能力）、facetSearch、租户 token——SDK 有而 v1 API 不暴露。
- 不验证 Boot 3/4 装配（M2/M3）；不引入任何新依赖。

## Decisions

### D-M1-1 taskUid 全链路 `int`（含公开 API）

SDK 实测 `TaskInfo.getTaskUid()` 返回 `int`，M0 已裁决"网关一律 int"。本设计将其贯穿到公开面：`createIndex`/`applySettings` 返回 `int` taskUid，`awaitTask(int)`、`getTask(int)`、`MeiliTask.getUid(): int`。
**备选（弃）**：边界处 `String.valueOf` 迎合设计文档 §5.3 原文——制造无意义转换面且掩盖真实类型；§5.3 在出口回写。

### D-M1-2 网关双通道：SDK raw API + 自有 OkHttp helper

`SdkMeiliRawGateway` 内部分两路，对 Operations 层透明：

1. **SDK 通道**（文档写/单读/搜/任务/索引增删）：`updateDocuments(String)`、`getRawDocument(String)`、`rawSearch(SearchRequest)`、`waitForTask(int,int,int)`、`getTask(int)`、`createIndex/deleteIndex`——spike 已验证的 raw 语义。
2. **raw HTTP 通道**（SDK 缺失或 typed-only 的端点）：`GET /indexes/{uid}/documents/count`、`POST /indexes/{uid}/documents/fetch`、`GET/PATCH /indexes/{uid}/settings`。共用一个包私有 OkHttp helper（base/key 取自 `client.getConfig()`，超时用 SDK 默认量级），响应错误体解析出 `code/message` 后统一翻译为 `MeiliIndexAccessException`。

**备选（弃）**：settings 走计划原文的 typed `Settings` 往返（Jackson 读→Gson 编码）——`Settings` 的 Java 双视图字段（`filterableAttributes`/`filterableAttributesConfig`）有 spikeA 实证的编码泄漏面，且 Jackson 重序列化引入与真实服务端 JSON 的形态漂移，M2 的 drift diff 依赖"服务端原文为事实源"；raw GET/PATCH 一个 helper 顺带覆盖三端点，代码更少。`PATCH /settings` 请求体由 `ProjectedSettings.toJson()` 直接产出（本就自产 JSON，无注入面）。

### D-M1-3 零泄漏守卫做成常驻测试而非人肉审查

`PublicApiLeakageGuardTest`（surefire，纯 JDK 反射）：扫描 `io.github.lamspace.meili.core` 下非 `internal` 包的全部 public 类型，断言其 public 构造/方法签名与 public 字段类型均不引用 `com.meilisearch.sdk.*`；类型清单来自 jar 遍历 classpath（无需新依赖）。
**备选（弃）**：ArchUnit（新依赖）、maven-enforcer（看不见签名粒度）、出口人工审查（不可重放）。守卫同时把"出口标准"变成每次 `mvn verify` 的持续事实。

### D-M1-4 序列化接口只暴露 `write/read` 两方法

搜索信封（`MeiliSearchResult.from`）的元数据解析由 core 内部私有 `ObjectMapper` 完成（信封字段皆小数值/布尔/字符串，无精度问题），hits 数组逐节点 `toString()` 后交给注入的序列化器。接口不出现 `JsonNode`/mapper 类型。
**理由**：M3 的 Jackson3 实现只需满足两方法即整体接管；否则 tools.jackson 类型会顶穿抽象。
**备选（弃）**：接口暴露 `readNode(JsonNode)`——把 Jackson2 类型写进本应格式中立的 SPI。

### D-M1-5 `multiSearch` v1 = 串行循环 `search`

SDK 的 `multiSearch` 仅有 typed 通道（`Results<MultiSearchResult>`，hits 过 Gson Map → Long 精度必损），无 raw 变体。v1 语义 = 按序逐 query 调 `search()`，javadoc 显式声明**非原子、无服务端合并**。
**备选（缓）**：自建 `POST /multi` 走 D-M1-2 的 HTTP helper——留待真实需求（federated 分页）出现，避免 M1 扩面。

### D-M1-6 计划"回改"项前置到宿主任务

`MeiliQuery.filterAdd(String)`（AND 累积到 filterDsl）进查询契约；`MeiliEntityCallbacks.registeredCount()` 进回调契约——原计划把它们散布在 Task 13/18 的备注里，执行顺序上必然踩空，现钉入所属能力 spec 与任务。

### D-M1-7 投影输出格式稳定性 = golden 逐字节

`ProjectedSettings.toJson()`：固定键序（searchable→filterable→sortable→displayed→passthrough 键按字典序）+ 稳定 pretty printer；`nothing declared → null 数组且不输出键`。M2 的 drift diff 与审计日志依赖该字节稳定性，故用 golden 文件锁死而非行为断言。

### D-M1-8 工程门禁随 Task 4 即时生效

首个主源码任务交付时删除 core pom 的 javadoc `<skip>`：全部 public/private 成员按 CLAUDE.md 契约分量级 Javadoc（`MeiliRawGateway` 标内部 SPI；元模型类写线程模型；投影写纯函数声明）；src/main 注释仅引用公开可查事实（SDK 类名、HTTP 端点），内部任务号/节号由 citations 门禁拦截。

## Risks / Trade-offs

- [HTTP helper 绕过 SDK 异常体系，错误形态不一致] → helper 内解析 API error body 后经与 SDK 通道同一 `MeiliErrors.translate` 出口，IT 对 404/400 各断言一次 `MeiliIndexAccessException.getMeiliCode()`。
- [v1.49.0 对 `POST /documents/fetch` 的 sort/limit/offset 语义与假设不符] → Task 11 IT 真机验证 findAll(filter+sort) 全链路；若服务端不支持某参数，收窄 `DocumentsFetchQuery` 并回写 spec（范围收缩预案，不猜）。
- [`displayedAttributes` 一旦声明即服务端白名单，用户误期] → 投影 spec 显式书写该后果 + Javadoc + golden 固化。
- [record 组件注解跨 JDK 传播差异] → 以本仓 JDK 25 编译 + release 17 的测试为契约（POJO/record 双形态断言），不依赖推理。
- [rawSearch(SearchRequest) 请求侧编码经默认 Gson] → spikeA 对照组与 spikeB 已实跑通过；哨兵 IT 常驻，SDK 升级变红即触发重实证。
- [串行 multiSearch 与用户直觉（原子/性能）不符] → javadoc 限制声明 + M3 文档"限制清单"转录；接口签名不变，未来可无破坏替换。

## Migration Plan

纯新增代码，无部署与回滚议题；每个任务一次 commit（conventional），出口回写设计文档 §5.3 与计划复选框。回退 = revert 任务 commit，无状态残留。

## Open Questions

（无——影响 spec/任务拆分的未知已由本设计 D-M1-1…8 裁决；余下如"批量分片大小"属 M4+ 议题。）
