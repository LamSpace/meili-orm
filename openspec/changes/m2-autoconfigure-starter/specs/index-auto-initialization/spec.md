# Spec Delta

## Purpose

定义启动期索引自动初始化生命周期：按 `meili.index.auto-init` 三模式对扫描到的实体执行建索引与 settings 投影同步，漂移按 `on-settings-drift` 三策略处置，并对启动期异常（不可达、索引名冲突、drift=fail）采取 fail-fast。

## ADDED Requirements

### Requirement: 初始化模式语义

`auto-init=none` 时系统 SHALL 不执行任何索引操作、不发服务端请求；`create-if-missing` 时：索引不存在 SHALL 创建（主键名取元模型）、投影非空 SHALL 推送 settings 并阻塞等待任务终态，索引已存在 SHALL 只 diff 不写入；`sync-settings` 时：对已存在索引执行 diff（见"diff 只比投影声明键"）。空实体集合 SHALL 为 no-op。

#### Scenario: 缺失索引被创建并推 settings

- **WHEN** auto-init=create-if-missing 且目标索引不存在、实体声明了角色注解
- **THEN** 索引被创建、投影 settings 被推送、初始化等待对应任务完成

#### Scenario: 已存在索引不被写

- **WHEN** auto-init=create-if-missing 且索引已存在
- **THEN** 不发出 create/update 请求，仅按漂移策略报告 diff

#### Scenario: none 零请求

- **WHEN** auto-init=none
- **THEN** 初始化期间对网关无任何交互

### Requirement: diff 只比投影声明键

settings diff SHALL 仅比对投影声明过的键（投影未声明的键永不视为漂移、永不下发）；无漂移 SHALL 记 DEBUG 且无任何写操作。数组比较中 `searchableAttributes` 顺序敏感，其余数组按集合等值。

#### Scenario: 未声明键漂移被忽略

- **WHEN** 服务端 rankingRules 与投影文件不一致而投影未声明该键
- **THEN** 不报告漂移、不写入

### Requirement: 漂移三策略

检出漂移时：`warn` SHALL 以 WARN 日志列出差异键且不写入；`fail` SHALL 抛映射异常使启动失败且不发出任何写请求；`apply` SHALL 推送投影 settings 并等待任务，且差异涉及 `filterableAttributes` 或 `sortableAttributes` 时 SHALL 额外 WARN 全量重建代价。

#### Scenario: apply 更新 filterable 触发重建告警

- **WHEN** drift=apply 且漂移含 filterableAttributes
- **THEN** settings 被推送、任务被等待、日志含重建代价告警

#### Scenario: fail 不写

- **WHEN** drift=fail 且存在漂移
- **THEN** 启动失败，网关无 updateSettings 交互

#### Scenario: warn 只报告

- **WHEN** drift=warn 且存在漂移
- **THEN** 日志列出差异键，无 updateSettings 交互

### Requirement: 启动期 fail-fast 边界

索引初始化在启动生命周期内同步执行，以下情形 SHALL 使应用启动失败：服务端不可达或初始化请求异常（错误类型为索引访问异常，不得静默跳过）；扫描集合中多个实体声明同一 `indexName`。`auto-init=none` 时上述不可达检查不适用（零请求，启动成功）。

#### Scenario: 不可达即启动失败

- **WHEN** auto-init 为默认值但配置的 meili.url 无服务响应
- **THEN** 应用启动失败并抛索引访问异常

#### Scenario: none 模式下不可达仍可启动

- **WHEN** auto-init=none 且服务不可达
- **THEN** 应用正常启动，上下文可用

#### Scenario: 索引名冲突拒绝启动

- **WHEN** 两个 @MeiliDocument 实体声明相同 indexName
- **THEN** 启动失败并抛映射异常，异常信息含冲突的索引名与实体类名

### Requirement: 不标注不声明的全链路保持

实体未声明任何字段角色且无 @MeiliSetting 透传时，初始化 SHALL 创建索引但 SHALL NOT 下发 settings（服务端默认不被覆盖）。

#### Scenario: 裸实体只建索引

- **WHEN** 实体仅有 @MeiliDocument 与 @MeiliId、索引不存在
- **THEN** createIndex 发生，updateSettings 零调用
