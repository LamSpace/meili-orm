# Proposal

## Why

设计文档非目标表将"审计（@CreatedDate 等）"标注为"归入 M4 之后再评估"，M4 已闭环而评估从未执行，是设计文档中唯一到期的悬置决策。本次评估的结论为落地 v1：时间戳审计是 spring-data 系用户的高频预期，而 MeiliSearch 无服务端时间戳等价物、客户端写入路径填充是唯一可行通道；填充点（save 前置）与元模型校验点（实体解析期）在现有架构中均有明确位置，实现不引入新依赖。

## What Changes

- 新增注解 `@CreatedDate`、`@LastModifiedDate`（core mapping 包，`@Target(FIELD)`，与 `@MeiliId`/`@MeiliField` 同族）。
- 写入路径填充：`save`/`saveAll` 在 `BeforeConvertCallback` 之前完成填充——created 仅当为空（对象 null / 原始 long 为 0）时填当前时刻，modified 每次覆盖；回调与序列化看到的即最终值。
- 实体形态双支持：POJO 就地反射写值（返回同一实例）；record 经规范构造器重建新实例（非审计组件值逐一保留）。
- 启动期 fail-fast：审计字段类型不在许可集（`Instant`/`OffsetDateTime`/`ZonedDateTime`/`LocalDateTime`/`long`/`Long`）时实体解析即抛 `MeiliMappingException`。
- 明确不做：`@CreatedBy`/`@LastModifiedBy`（无认证上下文，不臆造）、可插拔时钟/`AuditorAware` 集成、读路径与 delete 触碰、嵌套对象内审计字段。

## Capabilities

### New Capabilities

- `core-entity-audit`：审计注解集、类型校验 fail-fast、写入路径填充语义（幂等 created / 覆盖 modified）、POJO 与 record 两形态、与既有回调链的顺序契约。

### Modified Capabilities

（无——`core-entity-mapping` 与 `core-entity-callbacks` 既有 requirement 文本不变；审计能力自含其校验与顺序契约。）

## Impact

- **代码**：`meili-orm-core`（mapping 注解 + `MeiliPersistentEntity`/`MeiliPersistentProperty` 解析 + `DefaultMeiliSearchOperations` 写路径填充钩子）；autoconfigure 零改动（无新 bean）。
- **测试**：L1（解析/校验/两形态填充/时间窗断言）、L3 真机 IT（save→findById→再 save 的 created 稳定 / modified 推进，`wait-task=true`）。
- **文档**：`docs/mapping-guide.md` 审计章节、README 功能表行、限制清单补"upsert 无法区分插入/更新 → created 为空值填充的近似语义"与"无 createdBy"两条。
- **兼容性**：纯新增；未标注实体行为零变化，settings 投影不读取审计标记（除非字段另标角色注解）。
