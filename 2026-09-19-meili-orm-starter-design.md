# meili-orm：MeiliSearch Spring Boot Starter 设计文档与实施路线图

- 状态：待终审
- 日期：2026-09-19
- 作者：lam + Claude（brainstorming 产出）
- 决策基线：功能边界 = 核心三件套 + 索引自动初始化/Settings 同步（首版）+ Repository（后置里程碑）；不做响应式；序列化 = 可插拔接口、Jackson 默认；Boot 兼容 = 方案 A（单代码库双兼容 + IT 矩阵护栏）

---

## 1. 目标与非目标

### 1.1 目标

把 MeiliSearch 官方 Java SDK（`com.meilisearch.sdk:meilisearch-java`）封装为**类 Spring Data Elasticsearch 风格的 Spring Boot Starter**：注解声明式映射、模板化 Operations、自动配置、索引/settings 自动同步，**同一个 jar 同时兼容 Spring Boot 3.5.x 与 4.x**（JDK 25 构建、字节码基线 17）。

### 1.2 非目标（v1 明确排除）

| 排除项 | 原因 |
|---|---|
| 响应式（Mono/Flux）支持 | MeiliSearch SDK 是同步阻塞（OkHttp `execute()`），做响应式需另起 HTTP 层，成本高 |
| `@Version` 乐观锁 | MeiliSearch 无 seq_no/primary_term 等价物 |
| per-field 类型 mapping / analyzer | MeiliSearch 无此概念，文档字段类型由服务端推断（见 §2.3） |
| nested 关联查询、join/parent-child | 服务端无此能力 |
| 动态索引名（SpEL） | 留扩展位，v1 仅静态索引名 |
| 连接/读超时配置项 | SDK `Config` 硬编码 OkHttpClient 不可注入（调研实证）；诚实标注限制并向 SDK 上游提 issue |
| 审计（@CreatedDate 等） | spring-data-commons 能力，归入 M4 之后再评估 |

---

## 2. 调研结论（设计输入）

### 2.1 Spring Data Elasticsearch 的工程架构（对标物拆解）

分层：官方底层客户端（Boot3 = RestClient + ES java client 8.18 / Boot4 = `Rest5Client`/HttpClient5）→ `ElasticsearchOperations`（= DocumentOperations + SearchOperations + IndexOperations，实现 `ElasticsearchTemplate`）→ repository 层（spring-data-commons 集成：PartTree 派生 → CriteriaQuery、`@Query`、Pageable/Sort 翻译）。

自动配置装配链（starter 工程精髓，直接类比借鉴）：

```
spring.elasticsearch.* 属性
  → ElasticsearchConnectionDetails（可被 Testcontainers/服务发现替换的抽象）
  → RestClient(5)Builder（+ *BuilderCustomizer 扩展 SPI）
  → Transport + JsonpMapper（Jackson > Jsonb 条件择优）
  → ElasticsearchClient
  → ElasticsearchTemplate
  → SimpleElasticsearchMappingContext（EntityScanner 扫描 @Document 预注册）
  → converter / CustomConversions
  → Repository registrar（@EnableElasticsearchRepositories + 自动配置兜底）
```

每一环都是 `@ConditionalOnMissingBean`，用户声明同名 bean 即整体替换。

Boot 3→4 的演进事实（决定我们兼容策略）：
- starter 坐标不变，但自动配置**拆分为独立模块**（`spring-boot-elasticsearch`、`spring-boot-data-elasticsearch`）、类改名（`ElasticsearchDataAutoConfiguration → DataElasticsearchAutoConfiguration`）、传输层换代、Jackson 2→3；
- **属性前缀不变、`META-INF/spring/...AutoConfiguration.imports` 机制不变、repository 工厂层类集合稳定**（SDE 5.5.13 与 6.1.1 javadoc 对照一致）；
- 破坏集中在"客户端类型 + 序列化库"两层 → 官方靠**分层 + 条件装配**隔离版本漂移，我们照抄该思路。

### 2.2 Spring Data ES 映射功能全景与可移植性

| 功能 | SDE 实现 | 移植性判断 |
|---|---|---|
| 索引名（含 SpEL） | `@Document(indexName)` | ✅ 移植（SpEL 除外） |
| 主键 | commons `@Id` | ✅ 必须移植（Meili 主键推断规则多坑） |
| 索引设置 | `@Setting(settingPath/shards/refreshInterval)`（4.2 起从 @Document 拆出） | 🔶 shards/replicas 无对应；**settingPath 透传 = Meili settings JSON 完美对应** |
| 字段角色 | `@Field(type/analyzer/copyTo/...)` + `MappingBuilder` 生成 per-field mapping JSON | 🔶 重写为 **settings 投影**（角色声明 → searchable/filterable/sortable/displayed 数组） |
| 自动建索引 | repository 引导时 `createIndexAndMappingIfNeeded` | ✅ 语义相同，须附加"改 filterable/sortable 触发全量重建"的代价控制 |
| 实体↔文档转换 | `MappingElasticsearchConverter`（反射式，不经 Jackson）+ `CustomConversions` + `_class` 类型提示 | 🔶 框架思路移植；类型提示不需要（Jackson 承担转换） |
| 生命周期回调 | BeforeConvert/AfterConvert/AfterLoad/AfterSave 四件套，声明 bean 即生效 | ✅ 高价值，直接移植 |
| 字段改名/@Transient | `@Field(name)` 等 | ✅（字段排除改用 Jackson `@JsonIgnore`） |
| analyzer/copyTo/nested/geo/dense_vector 参数/ignore_above 等 | Lucene/ES 特性 | ❌ 不移植 |

注：调研纠正了若干常见误传——`settingPath/shards/replicas` 早已不在 `@Document` 上（迁至 `@Setting`）；`aliasesPath/similarity/sequenceNumberPrimaryTerm` 从未作为 `@Document` 属性存在；无 `@GeoField`（实为 `@GeoPointField`/`@GeoShapeField`）；`@Field` 无 `fields` 属性（多字段用 `@MultiField`+`@InnerField`）；公开 API 无 `RefreshAfterWriteCallback`、无 `AutoIndexCreator`。本文档以纠正后的事实为准。

### 2.3 MeiliSearch 服务端与 SDK 关键约束（调研实证）

- SDK：**0.21.0**（2026-07），最低 Java 17；HTTP = **OkHttp 5.3.2（`api` 作用域传递，连带 okio）**；序列化默认 **Gson 2.13.2**，自带可插拔 `JsonHandler`（`GsonJsonHandler` 不可定制；`JacksonJsonHandler` 可注入外部 ObjectMapper，Jackson 为 compileOnly 2.21.x）。
- **文档写入 API 只接受 JSON 字符串，不接受 POJO**；`getDocument(s)` 支持泛型（走 JsonHandler）。
- **search 返回 hits 是 `ArrayList<HashMap>`，Gson 把所有数字解成 `Double` → Long 主键丢精度**——读路径必须绕开该环节。
- Settings：`searchableAttributes`（顺序即字段权重）、`filterableAttributes`（新版 granular equality/comparison 开关）、`sortableAttributes`、`displayedAttributes`、`rankingRules`（索引级全局，无 per-field 分词器）、`synonyms/stopWords/distinctAttribute/typoTolerance/pagination/faceting/embedders/localizedAttributes`；每个子设置有 get/update/reset 三件套，update 为 PATCH 合并。
- 写操作（documents/settings/index 增删改）**全部异步**：立即返回 `TaskInfo`，`waitForTask(uid)` 是阻塞轮询（默认 5s 超时/50ms 间隔）。
- 主键规则：类型仅 integer/string；索引有数据后不可更换；同主键覆盖（天然 upsert）；服务端对 `id` 结尾字段做自动推断（多候选报错）。
- 无固定 schema：字段类型服务端推断、数字按 f64 存（约 15 位有效十进制，超大 Long 精度受损）、嵌套对象展平、对象字段整体替换。
- **修改 filterable/sortable 触发全量重建** → settings 同步只能发生在启动/迁移期，且必须可关、可告警。
- 索引在首次 `addDocuments` 时自动创建；删除支持 `deleteDocumentsByFilter`（服务端 1.2+）。
- 社区存在非官方同类项目 `io.vanslog:spring-data-meilisearch`，作对照参考而非依赖。
- 服务端本地镜像 **v1.49.0**（已确认存在），上游最新 1.53.2，SDK 声明兼容 v1.x；IT 与演示一律钉 **v1.49.0**。

### 2.4 本地环境基线

| 项 | 值 |
|---|---|
| OS / Shell | Linux, bash |
| JDK | Oracle JDK 25.0.3（构建用；字节码基线见下） |
| Maven | 3.9.16，**所有命令必须 `-s /home/lam/repo/settings.xml`，禁止使用默认配置** |
| 本地仓库 | `/home/lam/repo`（已缓存 Boot 4.0.3 / spring-data-commons 4.0.3 / spring-data-elasticsearch 6.0.3 / elasticsearch-java 9.2.5；**无 Boot 3.x 产物**，it-boot3 首跑需联网拉取） |
| MeiliSearch | Docker 镜像 `getmeili/meilisearch:v1.49.0`，当前**无运行容器**（7700 未监听） |
| 现状 | 空骨架：根 `pom.xml`（`io.github.lamspace:meili-orm:1.0-SNAPSHOT`，当前 source/target=25，M0 改为 `maven.compiler.release=17`）、空 `src/`、OpenSpec 已初始化（未使用）、**非 git 仓库** |

---

## 3. 总体架构

### 3.1 模块结构

```
meili-orm (根 pom, packaging=pom, io.github.lamspace)
├─ meili-orm-core                        注解/实体元模型/序列化抽象/MeiliQuery/Operations/回调/Settings 投影
│    依赖: meilisearch-java 0.21.0 + jackson-databind     ← 零 Spring
├─ meili-orm-spring-boot-autoconfigure   两级自动配置
│    依赖: core + spring-boot + spring-boot-autoconfigure ← 同一 jar 服务 Boot 3.5/4.x
├─ spring-boot-starter-meili-orm         纯聚合 pom（spring-boot-starter + core + autoconfigure）
├─ meili-orm-repository                  (M4) Repository 层——全工程唯一碰 spring-data-commons 的模块
├─ meili-orm-serializer-jackson3         (M3+) 可选 Jackson3 序列化模块，自带 imports 实现"加依赖即生效"
├─ it/meili-orm-it-boot3                 钉 spring-boot 3.5.16 的兼容 IT
├─ it/meili-orm-it-boot4                 钉 spring-boot 4.0.3 的兼容 IT
└─ examples/meili-orm-example-boot3      演示工程（Boot 3.5.16）
   examples/meili-orm-example-boot4      演示工程（Boot 4.0.3，与 boot3 同一套业务代码）
```

### 3.2 依赖纪律（方案 A 的命脉）

1. core 零 Spring 依赖（连 spring-core 都不引）；
2. autoconfigure 只允许引用两代 Boot 都稳定的底座：`@ConfigurationProperties`、`@ConditionalOn*`、`ObjectProvider`、`AutoConfiguration.imports` 机制——**绝不引用任何被 Boot 4 搬家/改名的类**（旧 ES 自动配置类、`EntityScanner` 等）；
3. `spring-data-commons` 被隔离到 M4 的 repository 模块。若届时确认 commons 3.5↔4.0 存在编译级冲突，**仅该模块**出 boot3/boot4 薄变体，其余模块不动；
4. 护栏是编译事实而非口号：`it-boot3`/`it-boot4` 各钉死一代版本独立编译运行，误引搬家类矩阵当场红。

### 3.3 核心数据流

**写路径**：
```
operations.save(entity)
  → BeforeConvertCallback 链
  → 序列化：MeiliDocumentSerializer.write(entity) → JSON 字符串   ← 不经 SDK Gson
  → index.updateDocuments(json)（upsert 语义，对标 ES index 直觉）
  → taskUid →（meili.wait-task=true 时 awaitTask 至终态）
  → AfterSaveCallback 链
```

**读/搜路径**：
```
operations.search(query, Book.class)
  → MeiliQuery → SDK SearchRequest
  → 服务端 → 原始 JSON 字符串（raw 接口/自有 JsonHandler 通道）
  → MeiliDocumentSerializer.read(json, Book.class)（Jackson 直接反序列化实体）
  → AfterLoad → AfterConvert
```
绕开 SDK 内部 `Gson→HashMap→Double` 环节，**Long 主键精度问题在架构上消失**；序列化实现可插拔，同时化解 Boot4 Jackson2/3 分裂。

**启动装配路径**：
```
meili.* 属性 / MeiliConnectionDetails bean
  → SDK Config（注入自有 JsonHandler 适配器）→ com.meilisearch.sdk.Client
  → MeiliDocumentSerializer → MeiliMappingContext（扫描 @MeiliDocument）
  → MeiliSearchOperations → EntityCallbacks 收集 → IndexInitializer（按开关执行）
```

### 3.4 SDK 硬约束与应对（风险前置）

| 约束 | 应对 |
|---|---|
| `Config` 内部自建 OkHttpClient，超时/连接池不可注入 | v1 接受默认值并写进文档「限制清单」；向 SDK 提 feature request/PR |
| 自定义 JsonHandler 后，SDK 内部模型（Settings/TaskInfo 等依赖 Gson typeAdapter）是否仍正确解析——未知 | **M0 spikeA 实证**；若不兼容，回退方案：Config 保留 GsonJsonHandler，文档/搜索路径全部走 raw 字符串 + 自有序列化（读写主链路本就不依赖 JsonHandler，回退代价低） |
| OkHttp5/okio 作为 api 传递依赖进入用户 classpath | 根 pom `dependencyManagement` 钉版本与 Boot BOM 对齐，文档说明 exclusion 方法 |
| 全工程无 git 仓库 | 待决策 D1（见 §9） |

---

## 4. 映射体系设计

职责边界一句话：**格式转换全权交给 Jackson，元模型只管「索引名/主键/字段角色」三件事**。MeiliSearch 无 per-field mapping，所以"映射"的落地形态 = **settings 投影**。

### 4.1 注解集（`io.github.lamspace.meili.core.mapping`，全部自研）

```java
@MeiliDocument(indexName = "books")                       // 索引名（v1 静态）
@MeiliSetting(settingPath = "classpath:meili/books.json") // 透传 settings（类级、可重复、可缺省）
class Book { ... }

@MeiliId                     // 主键属性；类型仅 String/整型；命名不必是 id，消除服务端推断坑
private Long id;

@MeiliField(name = "book_title",            // 文档字段改名（投影与 Jackson 序列化结果保持一致）
            searchable = true, searchableOrder = 1,  // 参与搜索 + 权重顺序（searchableAttributes 顺序敏感）
            filterable = true,               // → filterableAttributes（等值/比较开关 v1 走默认 equality）
            sortable = true,                 // → sortableAttributes
            displayed = true)                // → displayedAttributes
private String title;
```

- `@MeiliSetting(settingPath)` 是透传 settings 的**唯一载体**（可重复，类级）：完整 settings JSON（rankingRules/synonyms/stopWords/distinctAttribute/faceting/pagination 等），与角色投影合并时**透传优先**。（刻意不把 settingPath 放进 @MeiliDocument——SDE 早期正是这么做的，4.2 版本才拆出来，我们不再重复这段弯路。）
- 字段排除：使用 Jackson `@JsonIgnore`，不自造 `@MeiliTransient`。
- **语义铁律：不标注 = 不声明**。实体内至少有一个字段声明了某角色，才生成对应 settings 数组；绝不无中生有覆盖服务端默认。
- 嵌套对象属性在投影时展平为点路径（如 `author.name` 可声明 filterable/sortable），与 Meili 展平语义对齐。
- 启动期 fail-fast 校验（`MeiliMappingException`）：主键属性缺失/类型非法、`indexName` 多实体冲突、角色注解与透传 JSON 显式矛盾。

### 4.2 元模型与转换器分工

- `MeiliMappingContext`（纯 Java，按 Class 缓存）→ `MeiliPersistentEntity`：indexName、主键属性访问器、字段投影名/角色/点路径。**不做值转换**。
- `MeiliDocumentSerializer`（core 接口）：`String write(Object)` / `<T> T read(String, Class<T>)`。默认实现 `Jackson2DocumentSerializer`：autoconfigure 优先注入容器 `ObjectMapper` bean（Boot 3 常态），缺失则自建带稳定默认配置的实例（Boot 4 默认是 Jackson3，走自建路径，行为仍正确；全局 Jackson 定制偏好由 M3+ 的 jackson3 模块或用户自定义 bean 满足）。
- 实体形态支持：普通类 + **record**（record 的组件顺序/紧凑构造器由 Jackson 处理，元模型只读注解）。

### 4.3 生命周期回调（`io.github.lamspace.meili.core.event`，对齐 SDE 语义）

```java
public interface BeforeConvertCallback<T> { T onBeforeConvert(T entity, String index); }
public interface AfterSaveCallback<T>     { T onAfterSave(T entity, String index); }
public interface AfterLoadCallback<T>     { String onAfterLoad(String rawDocument, String index); } // 或 Document 包装
public interface AfterConvertCallback<T>  { T onAfterConvert(T entity, String index); }
```
core 提供轻量 `EntityCallbacks` 注册表（自研，不引 commons 的 EntityCallbacks）；autoconfigure 按泛型收集 bean。

### 4.4 Settings 投影管线（本设计的 "MappingBuilder"）

```
@MeiliDocument + 字段角色注解 + @MeiliSetting 透传文件
        │
        ▼
MeiliSettingsProjection（纯函数，golden-file 测试覆盖）
  → Settings{searchableAttributes(按 searchableOrder), filterableAttributes,
             sortableAttributes, displayedAttributes} ⊕ 透传 JSON（深合并，透传优先）
        │
        ▼ （仅启动初始化期执行，运行期零开销）
与 GET /settings 实际值 diff
  ├─ 无漂移 → no-op
  └─ 有漂移 → 按 meili.index.on-settings-drift 处置：warn（默认，日志列出 diff）
                                                | apply（updateSettings + waitForTask；
                                                       涉 filterable/sortable 时 WARN 全量重建代价）
                                                | fail（启动失败）
```

---

## 5. 自动配置与 Operations API

### 5.1 自动配置结构（`io.github.lamspace.meili.autoconfigure`）

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  → MeiliClientAutoConfiguration   @AutoConfiguration @ConditionalOnClass(Client)
      MeiliConnectionDetails（url/apiKey 抽象；@ConditionalOnMissingBean，
        Testcontainers @ServiceConnection 等可在 M4+ 接入）
      → SDK Config（+ MeiliConfigCustomizer SPI）→ com.meilisearch.sdk.Client bean
  → MeiliDataAutoConfiguration     @AutoConfiguration(after = MeiliClientAutoConfiguration)
      @ConditionalOnBean(Client)
      → MeiliDocumentSerializer / MeiliMappingContext / MeiliSearchOperations
      → EntityCallbacks 收集 / IndexInitializer（@ConditionalOnProperty meili.index.auto-init）
```

- 每个 bean 挂 `@ConditionalOnMissingBean`——用户声明即整体替换（含替换整个序列化策略）。
- M4 追加 `MeiliRepositoriesAutoConfiguration`（`@ConditionalOnClass(MeiliRepository)` + `meili.repositories.enabled`，兜底等价 `@EnableMeiliRepositories`）。
- Jackson3 接管机制：`meili-orm-serializer-jackson3` 模块自带 imports + `@ConditionalOnClass(tools.jackson.databind.ObjectMapper)` + `@ConditionalOnMissingBean(MeiliDocumentSerializer)`，排序在 `MeiliDataAutoConfiguration` 之前——主模块零 Jackson3 编译依赖。

### 5.2 配置属性（前缀 `meili.`，第三方规范，不蹭 `spring.*`）

| 属性 | 默认 | 说明 |
|---|---|---|
| `meili.enabled` | `true` | 总开关 |
| `meili.url` | `http://localhost:7700` | 服务地址 |
| `meili.api-key` | （空） | master key 或 API key |
| `meili.wait-task` | `false` | 写操作同步等待任务完成（"写后可查"语义） |
| `meili.wait-timeout` | `5s` | wait-task 超时 |
| `meili.index.auto-init` | `create-if-missing` | `none` / `create-if-missing`（不存在则建+全量推 settings） / `sync-settings`（存在则 diff） |
| `meili.index.on-settings-drift` | `warn` | `warn` / `apply` / `fail` |
| `meili.repositories.enabled` | `true` | M4 生效 |
| `meili.client-agents` | `meilisearch-java:0.21.0; meili-orm` | SDK User-Agent 扩展 |

有意不提供：`connect-timeout`/`socket-timeout`（SDK 限制，见 §3.4）。

### 5.3 Operations API（对标 `ElasticsearchOperations` 三段式）

```java
public interface MeiliSearchOperations {
    // ── DocumentOperations ─────────────────────────────
    <T> T save(T entity);                              // = updateDocuments（upsert）
    <T> List<T> saveAll(Iterable<T> entities);         // 单请求 JSON 数组
    <T> Optional<T> findById(Object id, Class<T> type);
    <T> List<T> findAll(Class<T> type, DocumentsFetchQuery query); // filter/fields/sort（POST /documents/fetch）
    <T> void deleteById(Object id, Class<T> type);
    <T> void deleteAll(Class<T> type);
    <T> long count(Class<T> type);                     // 走 /documents/count（SDK 未暴露则 core 直连该端点）

    // ── SearchOperations ───────────────────────────────
    <T> MeiliSearchResult<T> search(String q, Class<T> type);
    <T> MeiliSearchResult<T> search(MeiliQuery query, Class<T> type);
    <T> List<MeiliSearchResult<T>> multiSearch(List<MeiliQuery> queries, Class<T> type);

    // ── IndexOperations / Tasks ────────────────────────
    <T> boolean indexExists(Class<T> type);
    <T> String createIndex(Class<T> type);             // 含 settings 投影推送，返回 taskUid
    <T> void deleteIndex(Class<T> type);
    void awaitTask(String taskUid);                    // 阻塞至终态，超时抛 MeiliTaskTimeoutException
    MeiliTask getTask(String taskUid);                 // core 自有不可变视图（uid/status/type/error/时间戳）
}
```

`MeiliQuery`（自有 IR，builder）：`q` / `filter(String DSL)` / `filterArray`（AND/OR 组）/ `sort` / `limit+offset` 与 `page/hitsPerPage` 两套分页 / `attributesToRetrieve` / `attributesToSearchOn` / `matchingStrategy` / `facets` / `showMatchesPosition` / `showRankingScore` / `vector`+`hybrid` / `distinct` / `raw(key, value)` 逃生舱 → 内部翻译为 SDK `SearchRequest`。
**设计理由**：SDK 类型不泄漏进业务代码；同一 IR 将成为 M4 方法名派生查询的目标（`PartTree → MeiliQuery`）。

`MeiliSearchResult<T>`：强类型 `hits`、`estimatedTotalHits`/`totalHits`、分页元数据、`facetDistribution`/`facetStats`、`processingTimeMs`、`rawJson()` 逃生舱。

异常体系（core，全部非受检）：
```
MeiliOrmException（根）
├─ MeiliMappingException        启动期元模型/settings 冲突，fail-fast
├─ MeiliIndexAccessException    服务端/网络错误（含 task error 详情透传）
└─ MeiliTaskTimeoutException    awaitTask 超时
```

### 5.4 构建与仓库规范

- **一切 mvn 命令：`mvn -s /home/lam/repo/settings.xml ...`**（M0 子任务：评估写入 `.mvn/maven.config` 使 `mvn verify` 自动生效，README 仍显式标注）；
- 字节码基线：根 pom 现 `source/target=25` → 改 `maven.compiler.release=17`（Boot4 与 SDK 共同底线），构建 JDK 仍 25；
- 版本基线：meilisearch-java 0.21.0、jackson-databind 2.x、Testcontainers（Boot BOM 管理版本）、it-boot3=3.5.16、it-boot4=4.0.3、MeiliSearch 服务端钉 v1.49.0；
- okhttp/okio/gson 传递依赖在根 pom `dependencyManagement` 统一钉版本。

---

## 6. 测试与验证（四层金字塔）

| 层 | 手段 | 覆盖 | 环境要求 |
|---|---|---|---|
| L1 单元 | JUnit5 + AssertJ + 金样例文件断言 | 元模型解析（类/record/嵌套点路径）、`MeiliSettingsProjection`（实体→settings JSON 逐字节 golden diff）、序列化器 **Long 主键往返精度钉死**、`MeiliQuery→SearchRequest`、回调链顺序 | 无网络、无 Spring |
| L2 自动配置 | `ApplicationContextRunner` | 条件链/back-off/`meili.enabled=false`/drift 三策略/ConnectionDetails 优先/用户 bean 替换 | 无真服务器 |
| L3 集成 | Testcontainers `GenericContainer("getmeili/meilisearch:v1.49.0")`（本地镜像已有，无需拉取；随机端口 + `@DynamicPropertySource` + 模块级单例容器） | 建索引→settings 同步→写→wait-task→搜→filter/sort/facet→分页→删；**改 filterable 触发重建的代价路径**；`awaitTask` 超时路径 | Docker |
| L4 兼容矩阵 | `it-boot3`（3.5.16）/ `it-boot4`（4.0.3）各跑 L2+L3 子集 + 上下文冒烟 | 双代装配等价性；M0 两个 spike 的结论固化为常驻测试 | it-boot3 首跑联网拉依赖 |

验证出口标准（每里程碑通用）：`mvn -s /home/lam/repo/settings.xml clean verify` 全绿 + 相关 demo 真机（Docker v1.49.0）手工冒烟通过。

M0 spike 输出要求：spikeA（JsonHandler 注入兼容性）、spikeB（raw→Jackson Long 精度链路）各产出一份 ≤1 页的结论记录，直接进入 §3.4 风险表对应行的"实证后处置"。

---

## 7. 示例演示（examples/）

两个 demo（boot3/boot4）**共享同一套业务代码**（依赖不同 BOM），以最少工程证明双代兼容：

- 实体 `Book`：`Long id`（主键类型坑覆盖）、`title`（searchable）、嵌套 `author{name,city}`（点路径 filterable 演示）、`tags[]`、`genre`（filterable+facet）、`price`（sortable）、`publishedAt`；`@MeiliSetting` 透传 rankingRules + synonyms 中文 stopwords。
- 场景脚本：
  1. 启动：日志可见自动建索引 + settings 投影推送（演示 drift=warn）；
  2. `POST /api/books/import`（批量导入，演示 `wait-task=true` 写后可查）；
  3. `GET /api/books/search?q=三体&genre=科幻&minPrice=30&sort=price:asc&page=0&size=10`（q+filter+sort+分页+facet 全链路）；
  4. `GET /api/books/{id}`、`DELETE /api/books/{id}`；
  5. 回调演示：`BeforeConvertCallback<Book>` 规整 title；
  6. 逃生舱演示：一个接口返回 `rawJson()`。
- README 一键流程：`docker run -d -p 7700:7700 -e MEILI_MASTER_KEY=... getmeili/meilisearch:v1.49.0` → `mvn -s /home/lam/repo/settings.xml spring-boot:run` → curl 集。

---

## 8. 里程碑路线图与子任务

> 工作量标注为参考粒度（单人 + AI 协作），出口标准是硬约束。
> **范围声明**：本文档是 **M0–M3 的实施计划输入**；M4（Repository）在 M3 出口达成后另立子 spec 与其自身的实施计划。

### M0 · 地基与风险清零（~0.5 周）
| # | 子任务 | 产出 |
|---|---|---|
| 0.1 | 多模块骨架（§3.1）、`release=17`、根 pom 依赖钉版（okhttp/okio/gson/jackson 对齐） | `mvn verify` 绿 |
| 0.2 | `.mvn/maven.config` settings 约定 + 本地裸连通冒烟（Docker 起 v1.49.0，SDK 直连建删索引一次） | 冒烟记录 |
| 0.3 | **spikeA：Config 注入自定义 JsonHandler 后 SDK 内部模型解析兼容性实证** | 结论 → 定读写通道主方案 |
| 0.4 | **spikeB：raw 响应 → Jackson 反序列化实体（含 Long/中文/嵌套）精度实证** | 结论 → 固化 L4 测试 |
| 0.5 | Testcontainers 集成基建（镜像常量、单例容器基类、`@DynamicPropertySource`） | L3 骨架可跑 |
| 0.6 | 决策 D1（git）落地：`git init` + 首提交（若批准） | 版本化开始 |

### M1 · meili-orm-core（~1 周）
| # | 子任务 |
|---|---|
| 1.1 | 注解集 + `MeiliMappingContext`/`MeiliPersistentEntity`（含点路径展平、启动期校验 fail-fast） |
| 1.2 | `MeiliDocumentSerializer` 接口 + Jackson2 实现 + Long 精度/命名策略测试 |
| 1.3 | `MeiliQuery`/`DocumentsFetchQuery` IR + 到 SDK 请求对象的转换层 |
| 1.4 | `MeiliSearchOperations`/`DefaultMeiliSearchOperations`：save/saveAll/findById/findAll/delete/count + raw 读通道 + taskUid/`awaitTask`/`MeiliTask` 封装 + 异常体系 |
| 1.5 | `MeiliSettingsProjection` + 透传合并 + golden-file 测试 |
| 1.6 | 回调四件套 + `EntityCallbacks` 注册表 |
| 1.7 | L1 全量 + L3 集成（真容器读写搜删全链路） |

**出口**：不依赖任何 Spring 的完整闭环测试绿；SDK 类型零泄漏核查（core 公开 API 审查，`SearchRequest`/`SearchResult`/`TaskInfo` 等 SDK 类型不得出现在签名中）。

### M2 · 自动配置 + Starter（~1 周）
| # | 子任务 |
|---|---|
| 2.1 | `MeiliProperties` + `MeiliConnectionDetails`/`PropertiesMeiliConnectionDetails` + `MeiliConfigCustomizer` |
| 2.2 | `MeiliClientAutoConfiguration`/`MeiliDataAutoConfiguration` 条件链 + back-off 全覆盖 |
| 2.3 | `IndexInitializer`（create-if-missing / sync-settings / drift=warn|apply|fail + 重建代价 WARN） |
| 2.4 | `spring-boot-starter-meili-orm` 聚合 pom + `additional-spring-configuration-metadata.json`（属性提示） |
| 2.5 | L2 `ApplicationContextRunner` 断言套件 |

**出口**：Boot 4.0.3 示例应用加依赖 + 3 行配置即用（`spring-boot-starters` 语义完整）。

### M3 · 双代验证 + 示例 + 文档（~1 周）
| # | 子任务 |
|---|---|
| 3.1 | `it-boot3`/`it-boot4` 兼容矩阵全绿（含 Boot3.5.16 依赖联网首拉验证） |
| 3.2 | examples 双 demo（§7 场景脚本全量） |
| 3.3 | `meili-orm-serializer-jackson3` 可选模块（含其 L2 条件测试） |
| 3.4 | 中文文档：README（含限制清单：超时不可配、count 语义、非目标表）、映射指南（注解→settings 对照表）、Boot3→4 升级说明 |

**出口**：两 demo 真机跑通并留 curl 记录；文档评审通过即为首版发布候选。

### M4 · Repository 层（后置，~2 周）
| # | 子任务 |
|---|---|
| 4.1 | spike：spring-data-commons 3.5↔4.0 在 `RepositoryFactorySupport/QueryMethod/EntityInformation` 的 API 差异定案 → 决定单模块或 boot3/boot4 薄变体 |
| 4.2 | `MeiliRepository<T,ID>` + `SimpleMeiliRepository` + FactoryBean + `@EnableMeiliRepositories` + repository 自动配置 |
| 4.3 | 派生查询：PartTree → MeiliQuery（等值/区间/In/True/Containing(→q)/Not + `top/distinct`）；`Pageable/Sort` → limit/sort |
| 4.4 | `@MeiliQuery` 方法注解（手写 filter DSL / q 模板 + SpEL 参数） |
| 4.5 | 双代 L3/L4 测试 + 文档章节 |

**出口**：双代下 CRUD + 派生查询 + 分页 IT 绿；commons 薄变体决策有书面依据。

---

## 9. 待终审决策（文档批准前需拍板）

| # | 决策 | 建议 |
|---|---|---|
| D1 | 当前目录**非 git 仓库**：是否 `git init` 并把本设计文档作为首个提交？ | 建议是（M0.6） |
| D2 | 坐标/命名：groupId `io.github.lamspace`、包根 `io.github.lamspace.meili`、starter 名 `spring-boot-starter-meili-orm` | 如无异议即定稿 |
| D3 | v1 排除项（§1.2 表：SpEL 动态索引名、@Version、响应式等）确认 | 建议确认 |
| D4 | 文档语言：正文中文，API javadoc 英文 | 建议是 |

---

## 10. 附录 · 调研来源

**Spring Data ES / Spring Boot**（starter 架构与映射层两报告）：
- docs.spring.io：spring-data/elasticsearch reference（mapping/entity-callbacks/versions/migration-guide-4.4-5.0）
- GitHub：spring-projects/spring-boot（v3.5.16 / v4.1.1 autoconfigure 源码与 AutoConfiguration.imports）、spring-projects/spring-data-elasticsearch（main/6.2 及历史标签注解与 MappingBuilder/MappingParameters/converter/event 源码核对）
- repo1.maven.org：spring-boot-starter-data-elasticsearch {3.5.16, 4.0.3} POM、spring-boot-{elasticsearch,data-elasticsearch} POM、spring-data-elasticsearch {5.5.13, 6.1.1} POM、spring-data-commons 4.0.0 release notes

**MeiliSearch**：
- GitHub meilisearch/meilisearch（main 源码：Config/Client/Index/Documents/Search/Tasks/Settings、json/GsonJsonHandler、json/JacksonJsonHandler、http/CustomOkHttpClient）
- Maven Central：meilisearch-java 0.21.0 POM/metadata
- docs.meilisearch.com：documents、design_primary_keys、filtering/sorting、datatypes、tasks
- GitHub meilisearch/meilisearch releases（服务端 v1.53.2；本地钉 v1.49.0）
- 对照：io.vanslog:spring-data-meilisearch（社区非官方，仅参考）
