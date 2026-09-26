# meili-orm 映射指南

[English](../mapping-guide.md)

一句话分工：**文档格式转换全权交给 Jackson；元模型只管"索引名 / 主键 / 字段角色"三件事**。
MeiliSearch 没有 per-field mapping 概念，所以"映射"的落地形态是 **settings 投影**——
注解声明的字段角色在启动期投影为服务端 settings 数组。

## 注解对照表

| 注解 | 对应 MeiliSearch 概念 | 要点 |
|---|---|---|
| `@MeiliDocument(indexName)` | 索引 uid | v1 仅静态名；多实体声明同一 indexName 启动即失败 |
| `@MeiliId` | 主键属性 | **必须且只能一个**；类型仅 String 或整型（`int/long/Integer/Long`）；命名不必叫 `id`，天然规避服务端 `xxxId` 自动推断的多候选坑 |
| `@MeiliField(name)` | 文档字段名 | 序列化/反序列化/投影三方共用一名；与 `@JsonProperty` 同时声明且不一致 → 启动失败（冲突裁决优先，优先级：`@MeiliField.name` > `@JsonProperty` > Java 名） |
| `@MeiliField(searchable=true, searchableOrder=n)` | `searchableAttributes` | 数组顺序即搜索权重（见下） |
| `@MeiliField(filterable=true)` | `filterableAttributes` | 等值过滤语义 |
| `@MeiliField(sortable=true)` | `sortableAttributes` | 排序声明 |
| `@MeiliField(displayed=true)` | `displayedAttributes` | **白名单效应**：只要有任何字段声明 displayed，投影数组即只含声明字段——未声明字段将不出现在搜索命中里（主键不受限）。谨慎使用 |
| `@MeiliSetting(settingPath)` | 完整 settings 透传 | 类级、可重复、`classpath:` 前缀（见"透传规则"） |
| `@CreatedDate` | 无（客户端写入路径填充） | 创建时间戳，仅现值为空时填充；近似语义见下"审计字段" |
| `@LastModifiedDate` | 无（客户端写入路径填充） | 修改时间戳，每次保存无条件覆盖 |
| Jackson `@JsonIgnore` | 字段排除 | 不入库、不参与投影；不另造 `@MeiliTransient` |

## 语义铁律：不标注 = 不声明

某角色数组**只有当至少一个非主键字段声明了它**才会生成；无人声明则该键在投影 JSON 中
缺席，settings 同步绝不覆盖服务端对应默认值。全部实体都只声明用到的角色。

## searchableOrder 排序规则

投影数组的装配顺序：

1. 声明了 `searchableOrder >= 0` 的字段按其值升序排在前；
2. 其余 searchable 字段（`order = -1`，默认）按点路径字典序排在后。

同一实体内显式 order 值重复会在启动期抛出 `MeiliMappingException`。
`filterableAttributes` / `sortableAttributes` / `displayedAttributes` 恒按点路径字典序。

## 嵌套对象与点路径

非简单类型（POJO/record）属性在投影时**展平为点路径**叶子：`@MeiliField(filterable=true)`
标注在 `Author.city` 上投影为 `"author.city"`，与 MeiliSearch 对嵌套文档的展平检索语义
对齐。规则：

- 简单类型（String/数字/日期/枚举/数组/Map 等）为叶子，可直接声明角色；
- 展开深度上限 3，自引用以 visited 集合截断；
- 集合/Map 整体为不透明叶子，不在其上继续展开；
- 能展开出子字段的聚合字段自身不得声明角色。

## @MeiliSetting 透传规则

- 载体是**类级可重复注解**（刻意不放进 `@MeiliDocument`，与字段角色职责分离）；
- `settingPath` 指向类路径 JSON 对象文件；文件缺失、非对象、含未知键都在启动期失败，
  错误消息携带文件名与具体键；
- 允许键白名单（18 项）：`searchableAttributes`、`filterableAttributes`、
  `sortableAttributes`、`displayedAttributes`、`rankingRules`、`synonyms`、`stopWords`、
  `distinctAttribute`、`typoTolerance`、`faceting`、`pagination`、`searchCutoffMs`、
  `dictionary`、`separatorTokens`、`nonSeparatorTokens`、`proximityPrecision`、
  `embedders`、`localizedAttributes`；
- 多个 `@MeiliSetting` 按声明顺序合并，后声明文件覆盖先声明；
- **透传优先**：透传文件声明了四个角色数组中的任何一个时，该数组以透传值为准、覆盖
  注解投影（其余键两路合并）；
- 透传键与角色注解显式矛盾（同一数组既投影又透传且值不同）不报错——按"透传优先"处置，
  这是有意的逃生舱语义。

## 投影管线

```
 @MeiliDocument + 字段角色注解 + @MeiliSetting 透传文件
        |
        v
 MeiliSettingsProjection（纯函数，启动期执行，运行期零开销）
   -> searchableAttributes(按 order)/filterableAttributes/
      sortableAttributes/displayedAttributes  ⊕ 透传 JSON（透传优先）
        |
        v   （auto-init != none 且投影非空时）
 与 GET /indexes/{uid}/settings 实际值 diff
   （只比对投影声明过的键：数组按集合等值、searchableAttributes 按序等值；
     未声明键永不参与比较、永不被写入）
        |
        +-- 无漂移 ------------------> DEBUG 日志，no-op
        +-- 有漂移 ------------------> 按模式与策略处置（下表）
```

## auto-init × on-settings-drift 行为矩阵

| | 索引不存在 | 索引存在、无漂移 | 索引存在、有漂移 |
|---|---|---|---|
| `auto-init=none` | 不交互 | 不交互 | 不交互 |
| `create-if-missing`（默认） | 建索引 + 推送投影 settings + 等待任务 | DEBUG | **只报告不写入**（warn 告警；`apply` 被抑制并明示；`fail` 抛异常终止启动） |
| `sync-settings` | 同上 | DEBUG | `warn`：告警列出漂移键；`apply`：推送投影 + 等待任务，涉 `filterableAttributes`/`sortableAttributes` 时额外告警全量重建代价；`fail`：抛异常终止启动 |

代价提醒：修改 filterable/sortable 触发服务端**全量重建**，settings 同步只应发生在
启动/迁移期——生产环境建议 `create-if-missing` 起步、变更窗口显式切 `sync-settings` +
`apply`（详见[限制清单](limitations.md)）。

## 生命周期回调四件套

| 接口 | 触发点 | 签名语义 |
|---|---|---|
| `BeforeConvertCallback<T>` | `save`/`saveAll` 序列化前 | 返回改写后的实体 |
| `AfterSaveCallback<T>` | 写任务受理（wait-task 时含等待终态）后 | 对每个已保存实体触发 |
| `AfterLoadCallback<T>` | 读到原始 JSON、反序列化**前** | 入参/返回都是文档 JSON 文本 |
| `AfterConvertCallback<T>` | 实体反序列化后 | 返回改写后的实体 |

写链：`（审计填充）→ BeforeConvert → 序列化 → 写请求 →（可选等待任务）→ AfterSave`；
读链：`取回原始 JSON → AfterLoad → 反序列化 → AfterConvert`。同链多回调按注册顺序执行；
目标实体类型按泛型实参匹配（父子类型均可命中）。

声明为 bean 即生效：

```java
@Configuration(proxyBeanMethods = false)
class BookCallbacks {

    /** 写前规整书名：以命名类声明泛型实参（lambda 的泛型被 JVM 擦除，无法自动解析）。 */
    static final class TrimBookTitle implements BeforeConvertCallback<Book> {
        @Override
        public Book onBeforeConvert(Book entity, String indexName) {
            return entity.title() == null ? entity : /* 重建规整后的 record */;
        }
    }

    @Bean
    BeforeConvertCallback<Book> trimBookTitle() {
        return new TrimBookTitle();
    }
}
```

## 审计字段（@CreatedDate / @LastModifiedDate）

客户端写入路径的时间戳审计。与角色注解**正交**（可共标、互不干扰；审计字段仍按普通
文档字段参与投影名与序列化，未另标角色注解则不进任何角色数组）。

| 注解 | 填充语义 |
|---|---|
| `@CreatedDate` | 仅现值为空时填当前时刻——对象类型看 `null`，原始 `long` 另把 `0` 视为未设置（哨兵值）；已有值原样保留 |
| `@LastModifiedDate` | 每次 `save`/`saveAll` 无条件覆盖为当前时刻（首次保存与 created 同刻） |

- 类型许可集六项：`Instant` / `OffsetDateTime` / `ZonedDateTime` / `LocalDateTime` /
  `long` / `Long`。`long`/`Long` 存 epoch 毫秒；时间类型以系统时区偏移包装同一时刻。
  许可集之外的类型在实体解析期抛 `MeiliMappingException`（消息含类名与字段名）。
- 填充时点：`save`/`saveAll` 解析实体后、**`BeforeConvertCallback` 之前**——回调与
  序列化看到的即最终值；填充恒先于全部用户回调，不可被用户回调重排。读路径与删除
  操作不触碰审计字段。
- 写回双形态：POJO 就地写字段、返回**同一实例**；record 经规范构造器重建新实例，
  非审计组件值逐项保留。record 的 compact constructor 随重建**重新执行**——要求构造器
  校验幂等（对合法填充值天然成立）；审计填充后重建必然再次经过该构造器。
- 仅实体顶层字段生效；嵌套对象内的审计字段不填充。
- 完全不含审计注解的实体，保存路径与引入本能力前一致（同一实例、零反射、无重建开销）。

> ⚠️ 近似语义：MeiliSearch 无服务端时间戳，upsert 也无法区分"插入 vs 更新"，
> `@CreatedDate` 是**空值填充**的客户端近似，不判定服务端存在性——客户端新建但携带
> 非空 created 值的实体将原样写入。边界见[限制清单](limitations.md)第 17–18 条。

## 启动期 fail-fast 校验清单

以下任一情形都在应用启动时抛 `MeiliMappingException`（消息含类名/字段名/文件名定位），
不留运行期 surprise：

- 主键缺失、多于一个、或类型非 String/整型；
- 多个实体声明同一 `indexName`；
- `@MeiliField.name` 与 `@JsonProperty` 冲突；
- 显式 `searchableOrder` 重复；
- `@CreatedDate`/`@LastModifiedDate` 字段类型不在六类型许可集（见"审计字段"）；
- 透传文件缺失、非 JSON 对象、含白名单外键；
- 回调 bean 无法解析目标实体泛型（lambda 形态）。

---

## Repository 层：派生查询与 `@MeiliQuery`

> 能力为 opt-in：应用显式引入 `meili-orm-repository` 坐标即自动启用（无需注解）；
> 亦可 `@EnableMeiliRepositories` 显式指定扫描包。开关属性 `meili.repositories.enabled`（默认 true）。

### 方法名语法

```
<动词>[Top<N>|First<N>][Distinct]By<条件链>[OrderBy<属性>(Asc|Desc)[And…]]
```

动词支持 `find` / `read` / `get` / `retrieve`（及其扩展形态如 `findPageBy…`）；
`count…By` / `exists…By` / `delete…By` 派生 **不支持**。条件链以大写 `And` / `Or` 定界，
`And` 优先级高于 `Or`（渲染时 OR 组自动加括号）。

### 关键字对照表（支持面）

| 方法名片段 | 渲染结果 | 参数 |
|---|---|---|
| `…Equals` / `…Is` / 裸属性 | `path = 值` | 1 |
| `…Not`（属性后） | `path != 值` | 1 |
| `Not…`（属性前） | `NOT (path = 值)` | 1 |
| `…In` | `path IN [v1, v2]`（空集合直接返回空结果，不发请求） | 1（集合/数组） |
| `…Between` | `path BETWEEN a AND b`（双端闭区间） | 2 |
| `…GreaterThan` / `…After` | `path > 值` | 1 |
| `…GreaterThanEqual` | `path >= 值` | 1 |
| `…LessThan` / `…Before` | `path < 值` | 1 |
| `…LessThanEqual` | `path <= 值` | 1 |
| `…True` / `…False` | `path = true` / `path = false` | 0 |
| `…Containing` / `…Like` | 全文 `q=值` + `attributesToSearchOn=[path]`（Like 通配符忽略，等价 Containing；一个方法至多一个） | 1 |
| `findTop<N>` / `findFirst<N>` | `limit(N)`（与 `Pageable` 共存时以分页为准并 WARN） | — |
| `OrderBy…Asc/Desc` | `sort("path:asc|desc")`，其后追加 `Pageable`/`Sort` 的排序 | — |

字符串值一律渲染为**转义后的双引号字面量**（`"` 与 `\` 加反斜杠）；数值/布尔裸写；
`LocalDate`/`LocalDateTime`/`OffsetDateTime`/`Instant` 按 ISO 文本裸写。方法参数为 `null`
的条件值直接报错（不支持可选条件）。

### 不支持面（启动期即报错，消息含方法名）

`StartingWith`、`EndingWith`、`RegularExpression`、`IsNull`、`IsNotNull`、`IsEmpty`、
`IsNotEmpty`、`Exists`、`IgnoreCase`、`Distinct` 修饰符（distinct 需指定属性，请用
`@MeiliQuery(distinct=…)`）、**属性缩写**（如 `findByAdrCity`——按实体字段字典最长前缀
切分，不猜缩写）、集合/对象属性上的等值条件、DTO 投影与 `Stream` 返回类型。

返回类型支持 `List<T>`、`Optional<T>`（多命中取首条并记 DEBUG）、`Page<T>`（必须带
`Pageable` 参数；`getTotalElements()` 为服务端估算值，且 `PageImpl` 有"总数至少覆盖当前页"
的固有钳制行为）。

### 投影名桥接（方法名属性 → 文档字段）

条件与排序中的属性链按**实体 Java 字段字典**逐段最长前缀切分（`AuthorCity` →
`author` → `city`），每段落名为 core 元模型的投影路径：`@MeiliField(name="book_title")`
使 `findByTitle…` 渲染 `book_title = …`；嵌套对象渲染点路径（`author.city`）。
`@JsonIgnore` 字段、`static` 字段、未知属性、聚合属性作条件目标，一律启动失败并定位方法。

### 启动期角色预检

| 条件族 | 要求实体声明 |
|---|---|
| 进入 filter 的属性（等值/IN/区间/比较/布尔/NOT） | `@MeiliField(filterable = true)` |
| 进入 sort 的属性（`OrderBy`） | `@MeiliField(sortable = true)` |
| `Containing`/`Like` 目标属性 | `@MeiliField(searchable = true)` |

任一缺失 → 启动失败（`MeiliMappingException`），消息给出两条修复路径：字段注解补声明，
或经 `@MeiliSetting` 透传在服务端声明。**预检判定源恒为实体声明**——透传 JSON 声明的角色
不进入预检输入（防实体与服务端静默分叉；若仅经透传声明，请同时补字段注解）。
主键属性上的条件豁免 filterable 预检（服务端按主键可寻址）。

### `@MeiliQuery` 注解查询

```java
@MeiliQuery(q = ":keyword", filter = "genre = :genre AND price > ?0", distinct = "authorId")
List<Book> brutal(@Param("genre") String genre, Double minPrice, @Param("keyword") String kw,
                  Pageable pageable);
```

- 占位符三形态：`?N`（只数"值参数"，`Pageable`/`Sort` 参数不参与）、`:name`
  （`@Param` 或编译参数名）、`#{…}` 原生 SpEL（变量以 `#name` / `#argN` 引用）。
- `filter` 模板中 String 值自动包引号并转义——模板作者**不写引号**（`genre = :g`），
  参数值无法改变 DSL 结构；数值/布尔裸渲染；`q` 模板中的值原样作为全文文本。
- `distinct` 只接受字面量投影路径（不接受占位符）。
- 与派生共存时注解短路方法名条件：`OrderBy` 与 `TopN` 仍取方法名，其余条件段忽略并启动 WARN。
- 启动期校验：模板非空、占位符可绑定、`filter` 括号配对；服务端 DSL 语法错误按
  `MeiliIndexAccessException` 透传。

### 无界读与批量删

`findAll()` / `findAll(Sort)` 走 documents/fetch 通道，受索引 `pagination.maxTotalHits`
（默认 1000）上限：恰好取满即记 WARN 声明可能截断。`deleteAll(Iterable)` /
`deleteAllById(Iterable)` 逐条单文档删除（每实体一个请求）。
