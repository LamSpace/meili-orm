# core-query-abstraction Specification

## Purpose
查询中间表示与强类型结果契约：`MeiliQuery`/`DocumentsFetchQuery` 能表达什么、非法组合如何拒绝、服务端响应如何解析为无损的 `MeiliSearchResult`。core 公开面不出现 SDK 请求对象类型。
## Requirements
### Requirement: 查询 IR 完整表达搜索参数

`MeiliQuery` SHALL 以链式 builder 表达：`q`、`filter(String DSL)`、`filterAdd(String)`（AND 累积）、filter 分组（组内 OR/组间 AND）、`sort`、`limit/offset` 与 `page/hitsPerPage` 两套分页、`attributes`、`attributesToSearchOn`、`matchingStrategy`、`facets`、`showMatchesPosition`、`showRankingScore`、`distinct`、`vector`、`hybrid`，以及 `raw(key, value)` 逃生舱；IR 字段 SHALL 能全部落到实际请求（服务端可见效果以真机 IT 反查）。

#### Scenario: filterAdd 累积 AND

- **WHEN** 连续 `filterAdd("genre = \"科幻\"")` 与 `filterAdd("price > 30")`
- **THEN** 最终过滤条件为两者 AND 组合，真机搜索只返回同时满足的命中

#### Scenario: IR 参数落到服务端

- **WHEN** 以 q+filter+sort+facets+showRankingScore 的查询执行真机搜索
- **THEN** 响应体现排序生效、facetDistribution 非空、hit 含 rankingScore

### Requirement: 非法查询组合 fail-fast

翻译/执行前 SHALL 拒绝：`limit/offset` 与 `page/hitsPerPage` 混设、filter DSL 与 filter 分组混设、`raw()` 使用白名单外键名——均抛 `MeiliOrmException`（根类型）且消息指明冲突双方或非法键名。

#### Scenario: 分页模式互斥

- **WHEN** 同一 `MeiliQuery` 既设 `page(1)` 又设 `limit(5)` 后进入翻译
- **THEN** 抛异常，消息同时提及两种分页模式

#### Scenario: 未知 raw 键拒绝

- **WHEN** `raw("nonsense", 1)` 后进入翻译
- **THEN** 抛异常且消息含 `nonsense`

### Requirement: 强类型搜索结果无损

`MeiliSearchResult<T>` SHALL 由原始响应 JSON 构造：hits 经注入的序列化器逐条反序列化（Long 主键无损），信封字段（`estimatedTotalHits`/`totalHits`/分页五元组/facetDistribution/processingTimeMs/query）按服务端形态可空呈现，`getRawJson()` SHALL 原样返回响应全文；信封解析不得使用任何数值精度有损的中间 Map 通道。

#### Scenario: hits 大数主键无损

- **WHEN** 以含 `"id":9007199254740993` 的 raw 响应构造结果
- **THEN** `getHits()` 首元素主键逐位相等，且 `getEstimatedTotalHits()` 正确

#### Scenario: 可置信的空信封字段

- **WHEN** 服务端响应不含 `totalPages`（非分页模式）
- **THEN** `getTotalPages()` 返回 null 而非 0/异常

### Requirement: DocumentsFetchQuery 表达 fetch 语义

`DocumentsFetchQuery` SHALL 表达 filter、fields、sort、offset/limit，用于 `findAll` 的"按条件取文档"语义；其字段 SHALL 有服务端真机等效实现（POST /documents/fetch 形态），不支持项不进入 IR。

#### Scenario: filter+sort 取回文档

- **WHEN** 以 filter+sort 的 fetch 查询真机执行 findAll
- **THEN** 返回条数受 limit 约束、顺序符合 sort、内容符合 filter

