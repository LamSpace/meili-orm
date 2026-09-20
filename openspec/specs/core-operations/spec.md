# core-operations Specification

## Purpose
Operations 门面与传输层契约：文档写读删、搜索、索引生命周期、任务封装的对外行为，内部网关的通道裁决，以及"SDK 类型零泄漏"的常驻守卫。
## Requirements
### Requirement: 写操作 upsert 语义与同步等待

`save` SHALL 以 upsert 语义（updateDocuments 通道）发送实体序列化后的 JSON；`saveAll` SHALL 将同类型多实体合并为**单请求** JSON 数组；主键为 null 的实体 SHALL 在触网前抛 `MeiliOrmException`（消息含"主键"）；配置 wait-task 时 SHALL 阻塞至任务终态后才返回，并依次触发 BeforeConvert/AfterSave 回调。taskUid 全链路 SHALL 为 `int` 类型。

#### Scenario: 写后可查

- **WHEN** wait-task=true 下 save 一条新文档后立即 findById
- **THEN** 文档可读回且 Long 主键逐位无损

#### Scenario: 批量单请求

- **WHEN** saveAll 两个实体
- **THEN** 网关收到一次包含两个文档的数组请求（非两次单写）

#### Scenario: 空主键不触网

- **WHEN** save 主键为 null 的实体
- **THEN** 抛异常且网关零交互

### Requirement: 读操作原始通道与回调链

`findById` SHALL 经 raw 字符串通道取文档：命中走 AfterLoad→反序列化→AfterConvert，未命中（404）返回 `Optional.empty()` 而非异常；`findAll(Class, DocumentsFetchQuery)` SHALL 支持 filter/fields/sort/分页取回强类型列表；`count` SHALL 返回整型总数且不经任何浮点中间形态（服务端 documents/count 端点直连）。

#### Scenario: 缺失文档空 Optional

- **WHEN** findById 一个不存在的 id
- **THEN** 返回 empty，不抛异常

#### Scenario: 大数主键读回无损

- **WHEN** findById `9007199254740993`
- **THEN** 实体主键逐位相等（证明未经 Gson Map/Double 通道）

### Requirement: 搜索操作强类型返回

`search(String, Class)` 与 `search(MeiliQuery, Class)` SHALL 返回 `MeiliSearchResult<T>`；`multiSearch` 在 v1 SHALL 按序串行执行各查询并逐个返回，Javadoc 声明其非原子性；索引名 SHALL 由实体元模型解析，调用方不感知索引字符串。

#### Scenario: 全链路搜索

- **WHEN** 真机以 q+filter+sort+facets 查询
- **THEN** hits 顺序、facetDistribution、分页元数据符合请求，实体字段无损

### Requirement: 索引生命周期操作

`indexExists` SHALL 以布尔呈现存在性（不存在索引的服务端错误不得抛穿）；`createIndex` SHALL 以元模型主键名建索引、投影非空时推送 settings，返回最后一步 taskUid（int）；`deleteIndex`/`applySettings`/`projectedSettings` SHALL 可用；`deleteAll(Class)` 清空文档不删索引。

#### Scenario: 建索引附带投影

- **WHEN** 对声明了角色的实体 createIndex
- **THEN** 索引存在且服务端 settings 含投影数组；对无角色实体不产生 settings 写请求

### Requirement: 任务封装与超时

`awaitTask(int)` SHALL 阻塞轮询至终态或超时（超时抛 `MeiliTaskTimeoutException`，消息含 uid 与超时量）；`getTask(int)` SHALL 返回不可变 `MeiliTask` 视图（uid/status 枚举/type/error 信息），SDK `Task` 类型不出现在该返回值签名中。

#### Scenario: 超时路径

- **WHEN** awaitTask 以小于任务耗时的超时执行
- **THEN** 抛 `MeiliTaskTimeoutException`

### Requirement: 异常翻译统一出口

服务端/网络/任务错误 SHALL 统一翻译为 `MeiliIndexAccessException`（携带 MeiliSearch 错误码可读取）或 `MeiliTaskTimeoutException`；SDK 异常类型（`MeilisearchException` 系）不得从 Operations 抛穿；翻译对 SDK 通道与网关自有 HTTP 通道一致生效。

#### Scenario: 错误码透传

- **WHEN** 访问不存在的索引触发服务端 `index_not_found`
- **THEN** 抛 `MeiliIndexAccessException` 且 `getMeiliCode()` 为 `index_not_found`

### Requirement: SDK 类型零泄漏常驻守卫

`io.github.lamspace.meili.core` 下非 `internal` 包的全部公开类型，其公开构造器/方法签名与公开字段类型 SHALL 不引用 `com.meilisearch.sdk.*`；该约束由常驻反射测试在每个 `mvn verify` 把关（守卫本身即测试代码，可豁免扫描其自身）。

#### Scenario: 守卫拦截泄漏

- **WHEN** 任一公开 Operations/IR/结果类型签名被改为引用 SDK 类型
- **THEN** 守卫测试失败并指出泄漏的类与成员

### Requirement: 网关为 SDK 唯一出入口（通道裁决）

SDK 客户端类型与 HTTP 细节 SHALL 全部收敛于 `core.internal` 网关：实体文档读写/搜索走 SDK raw 字符串 API；SDK 未暴露或仅 typed 通道的端点（documents/count、documents/fetch、settings 原始读写）走网关自有 HTTP；settings 读取 SHALL 返回服务端真实 JSON 文本供 diff（不得经 typed 模型重序列化引入形态漂移）。

#### Scenario: settings 读为原文

- **WHEN** 网关 getSettings 返回后直接 readTree 比对
- **THEN** 文本与服务端 GET /indexes/{uid}/settings 响应一致（含服务端扩展键，投影 diff 只认自己声明的键）

