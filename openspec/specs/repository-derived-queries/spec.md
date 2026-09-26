# repository-derived-queries Specification

## Purpose
定义方法名派生查询契约：自研方法名语法解析（经 spike 实证为双代 commons 唯一可行形态）把方法名片段翻译为既有 `MeiliQuery` IR 的逐条语义、Java 属性名到实体投影名的桥接规则、启动期角色预检与不支持面的失败形态。目标是"读方法名即知服务端查询行为"，且全部错误在应用启动时暴露而非首查时暴露。

## Requirements

### Requirement: 关键字到 MeiliQuery 映射

派生查询 SHALL 支持下列关键字并逐条满足映射语义（值字面量渲染规则与注解查询共用同一转义器）：`…Equals`/裸属性名 → 等值 filter；`…In` → `属性 IN [..]`（参数集合为空时短路返回空结果，不发请求）；`…Between` → `BETWEEN` 双端闭区间；`…LessThan`/`…GreaterThan` 及其 Equal 变体、`…Before`/`…After`（同对应比较）→ 比较 filter；`…True`/`…False` → 布尔字面量等值；`…Not…` → `NOT (…)`（缺失字段对 NOT 的服务端行为以真机测试钉死并转录文档）；`…And`/`…Or` → 括号嵌套的布尔组合（Or 组 MUST 加括号防优先级歧义）；`…Containing`/`…Like` → 全文 `q=值` 且 `attributesToSearchOn` 限定到该属性的投影名（Like 的通配符 v1 忽略，按 Containing 等价处理）。方法名 `OrderBy…Asc/Desc` SHALL 翻译为 `sort("投影路径:asc|desc")`。`findTopN`/`findFirstN` SHALL 翻译为 `limit(N)`。派生查询的目标 IR 为 `MeiliQuery`，执行经 `MeiliSearchOperations.search`，hits 读路径回调链生效。

#### Scenario: 等值与区间派生真机命中

- **WHEN** 真机索引上调用 `findByGenreAndPriceBetween(...)`（genre/price 均已声明 filterable）
- **THEN** 返回文档集与手写 `MeiliQuery.filter` 等价查询一致

#### Scenario: Containing 走全文通道

- **WHEN** `findByTitleContaining("三体")`（title 声明 searchable 且投影名为 `book_title`）
- **THEN** 查询以 `q=三体` + `attributesToSearchOn=[book_title]` 发出并命中含"三体"的 title 文档

#### Scenario: In 空集合短路

- **WHEN** `findByGenreIn(List.of())`
- **THEN** 返回空结果且无服务端请求发出（mock 网关/操作可证）

### Requirement: 投影名桥接

派生查询的属性路径解析 SHALL 以 core 元模型为唯一权威：方法名中的 Java 属性链（含嵌套链如 `AuthorCity`）经**实体反射字典的最长前缀切分**（不依赖 commons 属性模型，缩写不支持）解析为属性段序列后，每段 SHALL 映射到投影路径（`@MeiliField.name` 改名与嵌套点路径，如 `author.city`）再进入 filter/sort/attributesToSearchOn 渲染；切分或映射失败（Java 属性不存在、被 `@JsonIgnore` 排除、聚合属性作叶子、链断在简单类型上）SHALL 启动期抛错，消息含方法名、属性段与实体类名。SHALL NOT 出现两套字段名真相源——投影名以 core 为准。

#### Scenario: 改名属性派生正确投影

- **WHEN** 实体 `title` 标注 `@MeiliField(name = "book_title")`，仓库声明 `findByTitle("三体")`
- **THEN** 发出的 filter 为 `book_title = "三体"` 而非 `title = "三体"`

#### Scenario: 未知属性启动即炸

- **WHEN** 仓库声明 `findByNoSuchField(...)`
- **THEN** 上下文启动失败，异常消息可定位方法名与属性段

### Requirement: 启动期角色预检

派生查询引导时 SHALL 按 core 元模型的角色声明预检：进入 filter 组合的属性（等值/IN/BETWEEN/比较/布尔/NOT 及其嵌套）要求 `filterable`，进入 sort 的属性要求 `sortable`，Containing/Like 目标属性要求 `searchable`；任一缺失 SHALL 启动期抛 `MeiliMappingException`，消息含方法名、属性投影路径与两条修复指引（字段注解补声明，或经 `@MeiliSetting` 透传在服务端声明）。预检的判定来源是实体声明而非服务端实际 settings——透传通道声明的角色不进入预检输入，此时预检失败消息 SHALL 提示透传声明的可能性但维持启动失败（防止实体声明与服务端事实静默分叉）。

#### Scenario: 未声明 filterable 启动失败

- **WHEN** 实体 `genre` 无 `@MeiliField(filterable = true)` 而仓库声明 `findByGenre(...)`
- **THEN** 上下文启动失败，消息含"filterable"修复指引；同实体加声明后启动成功

#### Scenario: 角色齐备通过预检

- **WHEN** 派生查询涉及的全部属性均已声明对应角色
- **THEN** 预检通过，仓库 bean 正常注册

### Requirement: 分页、排序与返回形态

返回类型 SHALL 支持 `List<T>`、`Optional<T>`（多结果取首条并 DEBUG 记录，不抛异常）与 `Page<T>`。`Pageable` SHALL 翻译为 page 模式分页：`page = pageNumber + 1`、`hitsPerPage = pageSize`；`Pageable.getSort()` 与方法名 `OrderBy` 同时存在时方法名优先、`Pageable` 排序追加其后；`Page` 总数语义遵循仓库层估算契约。参数为 `null` 的属性条件 SHALL 在启动期拒绝（派生查询不支持可选条件组合）。

#### Scenario: Pageable 换算正确

- **WHEN** `findByGenre("科幻", PageRequest.of(2, 20, Sort.by("price")))`
- **THEN** 发出查询 `page=3, hitsPerPage=20, sort=["price:asc"]`

### Requirement: 不支持面启动期报错契约

下列形态 SHALL 在仓库引导期抛 `IllegalArgumentException`（消息含方法名与支持面提示），不得静默降级或运行期才失败：`StartingWith`/`EndingWith`/`Regex`、`IsNull`/`IsNotNull`/`Empty`/`NotEmpty`、`IgnoreCase`、**属性缩写**（如 `findByAdrCity`——反射字典不做缩写还原，消息须明示"不支持缩写"）、`Distinct` 修饰符（Meili 的 distinct 必须指定属性，方法名无属性来源——该能力归注解查询面）、集合/数组/Map 属性上的等值派生、DTO 投影与 `Stream` 返回类型。v1 支持的返回类型集合为上一条所列，其余返回形态同属本契约报错面。

#### Scenario: 不支持关键字定位报错

- **WHEN** 仓库声明 `findByTitleStartingWith("三")`
- **THEN** 启动失败，异常定位到该方法且消息说明 v1 不支持 StartingWith
