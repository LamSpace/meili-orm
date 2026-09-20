# Proposal: meili-orm M1 · meili-orm-core

## Why

M0 已把全部架构未知清零（spikeA 定案 raw 通道唯一契约、spikeB 定案 Long 精度无损、javap 签名锚点在案），但 `meili-orm-core` 的主源码至今为零——M2 自动配置、M3 双代矩阵、M4 Repository 全部消费 core 的公开 API（映射元模型、序列化 SPI、查询 IR、settings 投影、回调、Operations）。core 不落，后续一切只能停留在计划文本。M1 的使命是把"零 Spring 的完整功能闭环"以 TDD 落地成可测试、可审查的活契约，并把 M0 实证对计划文本的签名修正（taskUid 全链路 int、raw 方法名等）一次性钉死，避免修正口径散落在各任务里。

## What Changes

- 新增映射层：`@MeiliDocument`/`@MeiliSetting(s)`/`@MeiliId`/`@MeiliField` 注解集 + `MeiliMappingContext`/`MeiliPersistentEntity`/`MeiliPersistentProperty` 元模型（嵌套展平点路径、record 支持、启动期 fail-fast 校验），异常体系四件（`MeiliOrmException` 根 + Mapping/IndexAccess/TaskTimeout）。
- 新增序列化层：`MeiliDocumentSerializer` 接口（仅 `write`/`read` 两方法，为 M3 Jackson3 实现留等价可满足面）+ `Jackson2DocumentSerializer`（`@MeiliField.name` 与序列化一致性、Long 精度往返钉死、JSR-310 ISO 日期）。
- 新增查询层：`MeiliQuery`/`DocumentsFetchQuery`/`MatchingStrategy` IR + `MeiliSearchResult<T>` 强类型信封（hits 经序列化器无损、`getRawJson()` 逃生舱）；`internal.SdkQueryTranslator` 翻译到 SDK `SearchRequest`（SDK 覆盖面已 javap 实证：含 vector/hybrid/distinct/locales）；分页双模式互斥、`raw()` 白名单键校验。
- 新增 Settings 投影：`MeiliSettingsProjection` 纯函数 + `ProjectedSettings`（不标注=不声明铁律、searchableOrder 排序、透传深合并透传优先、未知键 fail-fast、golden 文件逐字节锁定）。
- 新增回调层：四件套 Callback 接口 + `MeiliEntityCallbacks` 注册表（泛型实参解析、注册序触发序、`registeredCount()`）。
- 新增 Operations 层：`MeiliSearchOperations`/`DefaultMeiliSearchOperations` 全集（save/saveAll/findById/findAll/delete/count/search/multiSearch/index 生命周期/awaitTask/getTask）+ `MeiliTask`/`MeiliTaskStatus` 不可变视图；`internal.MeiliRawGateway` 为 SDK 类型唯一出入口。
- **网关通道裁决（本变更决策，实现 M0 spike 结论）**：实体文档读写与搜索走 SDK raw 字符串 API（`getRawDocument`/`rawSearch`）；SDK 未暴露或 typed-only 的端点（documents/count、POST /documents/fetch、settings 的 GET/PATCH）走网关内统一 OkHttp raw HTTP helper，连接参数取 `client.getConfig().getHostUrl()/getApiKey()`（public 已实证）。理由：typed `Settings` 双视图字段（`filterableAttributesConfig`）在序列化端有 spikeA 实证的泄漏面，raw HTTP 同时保证 drift diff 以服务端真实 JSON 为事实源。
- **签名修正（钉死 M0 实证对设计/计划文本的回改）**：taskUid 全链路 `int`（公开 API `createIndex`/`applyTask` 返回 `int`，`awaitTask(int)`）；`getSettings` 契约返回 JSON 文本；出口时设计文档 §5.3 签名同步回写。
- **SDK 类型零泄漏从"人肉审查"升级为常驻守卫**：`core` 公开包（`internal` 除外）全部 public 成员签名经反射扫描，出现 `com.meilisearch.sdk.*` 即测试红。
- 工程门禁即时生效：core pom javadoc `<skip>` 随首个主源码任务删除（CLAUDE.md §5 私有能力全量 Javadoc），`check-source-citations.sh` 对新 src/main 即刻在册。
- 不改动：autoconfigure/starter 模块仍为占位（M2 范围）；不新增任何外部依赖（全部沿用 M0 钉版）；spikeA/spikeB 哨兵保持现状。

## Capabilities

### New Capabilities

- `core-entity-mapping`: 注解集、实体元模型与启动期校验——"投影名=序列化名"、点路径展平、主键规则与冲突 fail-fast 是可判证契约。
- `core-document-serialization`: 序列化 SPI 与 Jackson2 默认实现——Long 无损往返、改名一致、可注入 ObjectMapper 的自定义配置被尊重。
- `core-query-abstraction`: 查询 IR、SDK 翻译与强类型结果——IR 字段全覆盖 SDK、非法组合拒绝、信封解析无损。
- `core-settings-projection`: 实体注解→settings JSON 投影管线——不标注=不声明、透传优先、golden 逐字节锁定。
- `core-entity-callbacks`: 生命周期回调四件套与注册表——匹配规则、触发顺序、未解析泛型即拒。
- `core-operations`: Operations 全集、raw 网关、任务封装与异常翻译——SDK 类型零泄漏（守卫测试）、写后可查语义、count/fetch/settings 直连裁决。

### Modified Capabilities

（无——`module-build-foundation` 的 Javadoc 门禁要求 M0 已按"缺失即失败"书写，本变更删除 core 临时 `<skip>` 属实现落地，requirements 不变；`meili-it-infrastructure`/`sdk-read-path-evidence` 仅被复用。）

## Impact

- **文件**：`meili-orm-core/src/main/java/io/github/lamspace/meili/core/{mapping,serialize,query,settings,event,exception,task,operations,internal}/**`（全新）；测试对应包 + `src/test/resources/golden/*.json`；修改 `meili-orm-core/pom.xml`（删 javadoc skip）；出口回写设计文档 §5.3 与计划文档 M1 复选框。
- **依赖**：零新增——jackson-databind/jsr310、meilisearch-java、okhttp-jvm、junit/assertj/mockito/testcontainers 全部 M0 已缓存于 `/home/lam/repo`，正常不需要联网。
- **运行环境**：L3 IT 需 Docker 守护进程与本地 `getmeili/meilisearch:v1.49.0`、`testcontainers/ryuk:0.12.0` 镜像（均就位，M0 已处理拉取超时对策）。
- **下游**：M2（Task 12–14）消费网关与 Operations 公开签名（int taskUid 为破坏性口径的固定点）；Task 13 依赖 `registeredCount()`；Task 18 依赖 `MeiliQuery.filterAdd()`——两处原计划"回改"项已在 tasks 中前置到宿主任务；M3 Jackson3 模块受序列化接口两方法面约束。
