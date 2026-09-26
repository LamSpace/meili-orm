# core-entity-audit Specification

## Purpose

定义 meili-orm 客户端写入路径的时间戳审计契约：`@CreatedDate`/`@LastModifiedDate` 注解、实体解析期类型校验、save/saveAll 填充语义（created 空值填充、modified 每次覆盖）、POJO 与 record 两种实体形态的写回方式，及与既有回调链的顺序关系。

## Requirements
### Requirement: 审计注解与类型校验

系统 SHALL 提供字段级 `@CreatedDate` 与 `@LastModifiedDate` 注解，仅允许标注于类型属于 `Instant`、`OffsetDateTime`、`ZonedDateTime`、`LocalDateTime`、`long`、`Long` 的实体字段；实体解析期发现许可集之外的类型 SHALL 抛 `MeiliMappingException`（fail-fast，消息含类名与字段名）。审计字段 SHALL 仍按普通文档字段参与投影名与序列化。

#### Scenario: 合法类型解析成功

- **WHEN** 解析含 `@CreatedDate OffsetDateTime createdAt` 与 `@LastModifiedDate long updatedAt` 的实体类
- **THEN** 元模型标注两字段为审计字段，解析正常完成

#### Scenario: 非法类型启动报错

- **WHEN** 解析含 `@CreatedDate String createdAt` 的实体类
- **THEN** 抛出 `MeiliMappingException`，消息含该字段名

### Requirement: 写入路径填充语义

`save` 与 `saveAll` SHALL 对每个实体在 `BeforeConvertCallback` 之前完成填充：`@LastModifiedDate` 字段每次无条件设为当前时刻；`@CreatedDate` 字段仅当现值为空（对象类型 `null`，原始 `long` 为 `0`）时设为当前时刻，已有值保留。填充值 SHALL 为写入时刻的系统时钟（UTC 语义的时间类型、`long` 为 epoch 毫秒）。读路径与删除操作 SHALL NOT 触碰审计字段。

#### Scenario: 全新实体双时间戳填充

- **WHEN** 审计字段均为空值的实体被保存
- **THEN** 返回实体的 created 与 modified 均落在调用前后时间窗内，且序列化写入服务端的文档含该两值

#### Scenario: 重载后再保存 created 保留

- **WHEN** 实体保存后经读取再保存
- **THEN** created 与首次填充值一致未被覆盖，modified 被重设为新的当前时刻

#### Scenario: 用户预置 created 不被改写

- **WHEN** 保存前手工设置非空 created 值的实体
- **THEN** 该值原样写入，不被填充逻辑覆盖

#### Scenario: 回调观察填充后实体

- **WHEN** 注册 `BeforeConvertCallback` 并保存审计字段为空的实体
- **THEN** 回调收到的实体审计字段已完成填充

### Requirement: 实体形态写回契约

POJO 实体 SHALL 就地写回字段并返回同一实例；record 实体 SHALL 经规范构造器重建返回新实例，非审计组件的现值 SHALL 逐项保留。未标注任何审计字段的实体保存路径 SHALL 与引入本能力前完全一致（同一实例、无重建开销）。

#### Scenario: record 保存重建且组件保留

- **WHEN** 保存审计字段为空的 record 实体
- **THEN** 返回新实例（与原实例引用不同），审计字段已填充，其余各组件值与原实例相等

#### Scenario: 无审计实体零影响

- **WHEN** 保存不含审计注解字段的实体（POJO 或 record）
- **THEN** 返回实例与传入实例为同一引用，写入文档不含任何新增字段
