# autoconfigure-data Specification

## Purpose

定义 meili-orm 数据层自动配置契约：以已装配的 SDK Client 为前提，组装序列化器、映射上下文、实体回调注册表与 Operations 模板，并按容器环境择优接入 ObjectMapper 与用户回调 bean。
## Requirements
### Requirement: 数据层 bean 装配前提与产出

存在可用 SDK Client bean 时，上下文 SHALL 产出以下可注入协作 bean：`MeiliDocumentSerializer`、`MeiliMappingContext`、实体回调注册表、`MeiliSearchOperations`；Client 不存在时数据层自动配置 SHALL 不加载。

#### Scenario: 核心 bean 可注入

- **WHEN** 仅配置 `meili.url`（无真机连接）并启动上下文
- **THEN** 上述各类型 bean 均存在且唯一，`MeiliSearchOperations` 可直接注入使用

### Requirement: 序列化器 ObjectMapper 择优

`MeiliDocumentSerializer` 默认实现 SHALL 优先采用容器内的 `ObjectMapper` bean（存在时以其为基构建）；容器无 ObjectMapper 时 SHALL 自建稳定默认配置实例（日期 ISO 输出、未知字段忽略、`@MeiliField.name` 重命名生效）。

#### Scenario: 容器 ObjectMapper 生效

- **WHEN** 容器存在配置了 SNAKE_CASE 命名策略的 ObjectMapper bean
- **THEN** 序列化器写出的文档字段名为下划线风格

#### Scenario: 无容器 mapper 仍可用

- **WHEN** 容器无 ObjectMapper bean
- **THEN** 序列化器仍存在，Long 主键往返无损、日期按 ISO 字符串输出

### Requirement: 序列化器用户替换 back-off

用户声明任意 `MeiliDocumentSerializer` bean 时，自动配置 SHALL 不再产出默认实现，operations SHALL 使用用户实现。

#### Scenario: 用户序列化器获胜

- **WHEN** 上下文预置一个非默认 MeiliDocumentSerializer bean
- **THEN** 容器中序列化器为该用户实例，装配正常完成

### Requirement: 回调 bean 自动收集

容器中所有 `MeiliCallback` 标记接口的实现 bean SHALL 被收集进实体回调注册表并按其实体泛型实参参与读写操作生命周期；回调收集数量可观测（非零计数），未声明回调时注册表 SHALL 为空且不报错。

#### Scenario: BeforeConvertCallback 参与写路径

- **WHEN** 注册针对实体类型的 BeforeConvertCallback bean 且经该上下文保存实体（网关为 mock 时断言提交文档已含回调效果）
- **THEN** 回调注册表计数增加，回调在转换前被应用

### Requirement: 实体类路径扫描

系统 SHALL 提供 `@MeiliDocument` 实体扫描：在 Boot 自动配置包上下文中发现标注实体集合；无法确定扫描包（非 Boot 应用）时 SHALL 返回空集合并记 DEBUG 日志，不得抛错。

#### Scenario: 包内实体被发现

- **WHEN** 自动配置包内存在 @MeiliDocument 实体
- **THEN** 扫描结果包含该实体类且去重、顺序稳定

#### Scenario: 无包信息不炸

- **WHEN** 在非自动配置包环境（无包信息）执行扫描
- **THEN** 返回空列表，无异常抛出
