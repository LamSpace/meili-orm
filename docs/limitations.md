# meili-orm 限制清单

每条都是已实证的行为边界（对应测试/哨兵标注在括号内），并给出 workaround。
承诺范围之外的能力见 README「非目标」。

## 1. 连接/读超时不可配置

官方 SDK 的 `Config` 在构造期自建 OkHttpClient，超时与连接池**没有注入口**
（对 meilisearch-java 0.21.0 的构造面实测复核；源码直读 + spike 记录见 docs/spikes.md）。

**Workaround**：需要精细网络控制时，自行构造 `com.meilisearch.sdk.Client` bean
（meili-orm 的自动配置 `@ConditionalOnMissingBean` 会让位给用户 Client），或向 SDK
上游提注入 OkHttpClient 的 feature request。

## 2. count 语义：整索引计数

`operations.count(Type)` 直连 `GET /indexes/{uid}/stats` 取 `numberOfDocuments`，
**不支持带 filter 的计数**（`POST documents/fetch` 的 total 语义在该端点不存在）。

另注：`GET documents/count` 路由在钉版服务端代际实测被 `documents/{id}` 捕获为
`document_not_found` 而不可用，故走 stats——该事实由真机 IT 覆盖。

**Workaround**：带条件计数用 `search`（空 `q` + filter，offset 分页 `limit(1)`，
勿设 page/hitsPerPage——两套分页混用会被查询 IR 拒绝）读 `getEstimatedTotalHits()`。

## 3. multiSearch v1 串行执行

`multiSearch(List<MeiliQuery>, Type)` 逐条委托 `search`，无并发（javadoc 已标注）。
结果顺序与入参查询顺序一致。

**Workaround**：调用方并行化，或等后续版本提供批量端点适配。

## 4. Boot 4 下默认序列化通道是"自建 Jackson 2 实例"

meili-orm 主模块序列化基于 Jackson 2；Boot 4 应用的容器 ObjectMapper 默认是 Jackson 3，
此时默认实现找不到 Jackson 2 容器 bean，**自建实例**——行为正确，但用户在 Boot 4 上
对 Jackson 2 的全局定制不会自动传导（Boot 3 上容器 Jackson 2 mapper 会被优先采用）。

**Workaround**：需要 Jackson 3 语义（接管容器 ObjectMapper）时加依赖
`io.github.lamspace:meili-orm-serializer-jackson3`（"加依赖即接管"，见
[boot3-to-boot4.md](boot3-to-boot4.md)）；或自行注册 `MeiliDocumentSerializer` bean，
自动配置整体让位。

## 5. 写操作默认异步：写后不立即可查

MeiliSearch 全部写操作（文档/settings/索引增删）是异步任务，立即返回 taskUid。
`meili.wait-task=false`（默认）时 `save` 返回不代表可检索。

**Workaround**：开 `meili.wait-task=true`（"写后可查"语义，超时 `meili.wait-timeout`），
或保存后显式 `operations.awaitTask(taskUid)`（返回任务 uid 的 API：`createIndex` /
`applySettings`；`awaitTask` 超时抛 `MeiliTaskTimeoutException`）。

## 6. 修改 filterable/sortable 触发服务端全量重建

settings 中 `filterableAttributes` / `sortableAttributes` 变更会让 MeiliSearch **重建
整个索引**：数据量大时任务分钟级起步。`sync-settings + apply` 检出涉这些键的漂移时会
输出明确的代价 WARN，但不拦截执行。

**Workaround**：日常用默认 `create-if-missing`（对已存在索引只报告不写入）；需要变更
角色时选迁移窗口显式切 `sync-settings + apply` 并配 `wait-timeout` 放宽；不可接受
重建抖动的环境用 `fail` 策略把漂移变成启动门禁，人工走迁移脚本处置。

## 7. 回调 bean 不能用 lambda 声明泛型目标类型

回调注册表从实现类的**泛型签名**解析目标实体；lambda 实例的类型参数被 JVM 擦除，
注册即抛 `MeiliMappingException`（启动期暴露，非静默失效）。

**Workaround**：以命名类或匿名内部类实现四件套接口（推荐，见映射指南示例）；确需
lambda 时用 `MeiliEntityCallbacks.register(Book.class, (BeforeConvertCallback<Book>) ...)`
显式传实体类型并自行注册进 registry。

## 8. 服务端数值语义的精度边界（f64 操作面）

文档存储与回显保真：主键/数值原样写读经 raw 通道逐位无损（`9007199254740993` 由哨兵
IT 常驻验证）。但 MeiliSearch 对数值字段的**排序、过滤比较与 facets 统计按浮点语义**
工作（服务端文档声明约 15 位有效十进制）：超大整数参与 sort/range-filter 时的边界
行为由服务端决定，不受 meili-orm 控制。

**Workaround**：金额等十进制敏感值用 Double 可精确表达的量级或字符串字段承载；
超大整数主键仅做等值定位（本库主键路径无损），不作为 range 排序键。

## 9. SDK 传递依赖进入应用 classpath

meilisearch-java 以 `api` 作用域传递 okhttp（5.3.2；其 Maven 构件的 JVM 类在
`okhttp-jvm`，本工程已显式处理空壳问题）、okio、gson（2.13.2）。与宿主应用的
okhttp3/gson 版本治理可能冲突。

**Workaround**：常规 ` <exclusions>` 处理或 dependencyManagement 统一钉版。
**不要**排除 gson——SDK 内部模型解析固定依赖默认 `GsonJsonHandler`（spikeA 实证：
自定义 JsonHandler 与 SDK 内部 typed 模型不兼容，实体通道已全量走 raw 字符串，
但 SDK 自身环节仍用 Gson）。

## 10. 索引删除/文档删除同样受异步任务模型约束

`deleteIndex` / `deleteAll` / `deleteById` 返回即受理完成、未终态。`wait-task=true`
时上述方法内部等待；否则用 `awaitTask`。

---

# Repository 层

## 11. `Page.getTotalElements()` 是估算值

MeiliSearch 检索响应给出的是 `estimatedTotalHits`/`totalHits`（视服务端配置其一），
不是精确总数；`Page` 的分页总页数据此计算。另注意 commons `PageImpl` 的固有语义：
总数会被抬高到"至少覆盖当前页"（`offset + 本页条数` 更大时）。需要精确计数用
`count()`（stats 通道，整索引）。

**workaround**：面向用户展示"约 N 条/更多"文案；精确需求走 `count()` 或业务侧计数。

## 12. `findAll()` / `findAll(Sort)` 受 maxTotalHits 截断

documents/fetch 通道单次读取上限为索引 `pagination.maxTotalHits`（默认 1000）。
仓库实现取满即记 WARN 声明"可能截断"，不会静默假称全量。

**workaround**：全量遍历用分页游标（`findAll(PageRequest.of(n, size))`）或
`DocumentsFetchQuery` 直连 Operations。

## 13. `…Containing` / `…Like` 是全文近似，不是子串匹配

MeiliSearch 无子串 DSL，仓库将其渲染为全文 `q=值` + `attributesToSearchOn=[属性]`：
命中受分词、typo 容忍与 rankingRules 影响（如 `findByTitleContaining("三体")` 走全文
通道），与 Java `String.contains` 语义不完全一致；Like 的通配符在 v1 被忽略。

**workaround**：需要确定性前/后缀匹配的服务端能力（BEGINS WITH 等）不在 v1 支持面；
用 Operations 手写 `MeiliQuery` 或应用侧二次过滤。

## 14. `deleteAll(Iterable)` / `deleteAllById(Iterable)` 逐条请求

v1 每条删除一个请求（每个一条任务），大批量删除成本高。

**workaround**：整索引清空用 `deleteAll()`；批量需求关注后续版本网关批量化。

## 15. 派生查询关键字为子集，且角色预检只看实体声明

不支持：`StartingWith`/`EndingWith`/`Regex`/`Null`/`Empty`/`Exists`/`IgnoreCase`、
`Distinct` 修饰符、**属性缩写**、count/exists/delete 派生、DTO 投影、`Stream` 返回
（完整清单与替代写法见映射指南）。filter/sort 目标属性必须在实体上声明
`@MeiliField(filterable/sortable/searchable)`，仅经 `@MeiliSetting` 透传声明的角色
**不满足预检**（启动失败，消息会提示这一点）——实体声明是唯一判定源。

**workaround**：透传场景请同时补字段注解（两份声明一致是刻意的冗余检查）；
超出子集的条件用 `@MeiliQuery` 手写。

## 16. 方法名条件与 `@MeiliQuery` 共存时仅排序/top 来自方法名

注解声明后，方法名中其余条件段被忽略（启动 WARN 列出），静默共存可能导致
"改了注解忘了改名"的分叉。

**workaround**：注解方法请把方法名条件段删净，只保留 `OrderBy`/`Top` 后缀。
