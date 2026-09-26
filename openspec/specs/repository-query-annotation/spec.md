# repository-query-annotation Specification

## Purpose
定义 `@MeiliQuery` 注解查询契约：手写 filter/q 模板的参数绑定、注入安全的字面量渲染、启动期模板合法性校验，以及与派生查询的互斥优先级。注解查询是派生映射表覆盖不了的服务端能力（显式 distinct、原生 filter DSL）的受控逃生舱。

## Requirements

### Requirement: 注解属性面与查询合成

系统 SHALL 提供方法注解 `@MeiliQuery`：属性 `q`（全文表达式模板，默认空）、`filter`（Meili filter DSL 模板，默认空）、`distinct`（去重属性，默认空即不启用）。`q` 与 `filter` 可独立或组合使用，组合时为"全文命中 AND 属性收窄"语义；三者全空的方法 SHALL 启动期报错（注解存在必须携带查询意图）。`distinct` 非空时 SHALL 翻译为 `MeiliQuery.distinct(值)`。注解查询执行与派生查询同一通道（`MeiliSearchOperations.search`），读路径回调链与分页参数（`Pageable`）语义一致。

#### Scenario: q 与 filter 组合

- **WHEN** `@MeiliQuery(q = ":text", filter = "genre = :g")` 方法被调用
- **THEN** 发出查询同时携带 q 与 filter，命中集为二者交集（真机断言）

#### Scenario: 空注解启动失败

- **WHEN** `@MeiliQuery` 不带任何非空属性
- **THEN** 仓库引导期抛错并定位方法

### Requirement: 参数绑定与字面量渲染

模板内参数占位 SHALL 支持双轨：位置形式 `?0`…`?n` 与命名形式 `:name`（命名依赖参数 `@Param` 标注或 `-parameters` 编译产物名）；绑定经 SpEL 求值（属性访问与 toString，复杂表达式求值失败包装为 `MeiliOrmException` 并含表达式原文与方法名）。字面量渲染规则 SHALL 为：String 参数进入 `filter` 模板时按 Meili 字符串字面量规则自动加引号并转义 `"` 与 `\`（模板作者不写引号，渲染器负责包裹）；数值/布尔参数按裸字面量渲染；`q` 模板中的参数值作为全文文本原样传入（不参与 DSL 转义）。`Pageable` 与 `Sort` 类型参数 SHALL 被绑定通道识别为分页/排序载体而非模板变量，不参与占位符计数。

#### Scenario: 引号注入无害

- **WHEN** String 参数值为 `科幻" OR price > 0 --` 传入 `filter = "genre = :g"`
- **THEN** 渲染出的 filter 中该值整体为被转义的单字面量，查询合法返回且无 DSL 结构改变

#### Scenario: 占位符缺失启动报错

- **WHEN** 模板引用 `:missing` 而方法参数表无对应名（或 `?5` 越界）
- **THEN** 仓库引导期抛错，消息含占位符与可绑定参数清单

### Requirement: 启动期模板合法性校验

仓库引导期 SHALL 对每个 `@MeiliQuery` 方法执行：占位符集合与参数表可绑定性校验（上一条契约）、`filter` 模板的括号配对与 `q`/`distinct` 的非空合法性校验。DSL 语法的服务端校验不属启动期义务（服务端 400 经异常翻译为 `MeiliIndexAccessException` 透传）。

#### Scenario: 括号不配对启动失败

- **WHEN** `filter = "genre = :g AND (price > :p"`
- **THEN** 引导期抛错并定位该模板的括号问题

### Requirement: 与派生查询的优先级

方法同时具备 `By` 方法名片段与 `@MeiliQuery` 时，注解 SHALL 短路方法名派生：属性条件与 OrderBy 仍解析自方法名并合并（注解提供 filter/q 主体，方法名继续供排序与 top 语义）；除排序/top 外的方法名条件部分被忽略且 SHALL 在启动期以 WARN 声明被忽略的条件片段（防"注解改了、方法名还挂着旧条件"的静默分叉）。方法名含派生预检不支持的属性时，注解方法的预检范围 SHALL 以"实际生效条件集"为准。

#### Scenario: 注解优先且旧条件告警

- **WHEN** `findByTitleAndGenre` 同时标注 `@MeiliQuery(filter = "price > 10")`
- **THEN** 生效 filter 为注解内容，genre/title 条件不进入查询，启动日志 WARN 列出被忽略片段
