# Design

## Context

动机见 proposal.md - Why。塑造方案的事实：

1. 现有写路径顺序：实体元模型解析 → 主键提取 → `BeforeConvertCallback` 链 → 序列化 → 网关写入 →（可选 await）→ `AfterSaveCallback` 链。填充点唯一合理位置是回调之前。
2. MeiliSearch 无服务端时间戳、upsert 无"插入 vs 更新"可判定性（同主键覆盖，无 ETag/seq 等价物）——created 语义只能是客户端近似。
3. record 不可变：写回只能重建实例；core 零 Spring 约束下无第三方审计设施可借。

## Goals / Non-Goals

**Goals:**
- v1 最小闭环：两注解、两形态、三语义（空填/覆盖/保留）。
- 校验走既有 fail-fast 通道，不引入新异常类型。

**Non-Goals:**
- `@CreatedBy`/`@LastModifiedBy`/`AuditorAware`/可插拔时钟；嵌套对象内审计；update 精确判定；读路径/删除触碰。留待有真实需求再立变更。

## Decisions

### D1 注解与元模型
`@Target(FIELD)`（与 `@MeiliId`/`@MeiliField` 同族，record 组件注解落 FIELD 路径已由既有元模型处理）。`MeiliPersistentProperty` 增 `isCreatedDate()`/`isLastModifiedDate()`；类型许可集校验放 `MeiliPersistentEntity.of` 解析期（复用 `MeiliMappingException` 通道）。审计字段不参与 settings 角色投影（无角色标记即不入数组，与现行为一致）。

### D2 填充点：operations 私有钩子，非回调实现
在 `DefaultMeiliSearchOperations` 的实体解析之后、`onBeforeConvert` 之前调用 core internal 的 `MeiliAuditSupport.audit(entity, entityMeta, now)`。否决"实现成 BeforeConvertCallback 注册进 EntityCallbacks"：回调是用户可见扩展点且顺序在链尾可被用户回调重排，填充必须恒先于全部用户回调——内部钩子保证顺序契约不可破坏。`saveAll` 逐实体复用同一路径再组批。

### D3 写回双形态
POJO：`Field.setAccessible(true)` 直写（无 setter 要求；与既有 idValue 反射策略同族）。record：读各组件现值（canonical accessor）→ 替换审计组件 → `RecordConstructor` 反射重建。compact constructor 会随重建重新执行——契约写入 javadoc/文档，要求用户构造器校验幂等（对合法填充值天然成立）。

### D4 时间源：写死系统时钟
`Instant.now()` 为锚，按字段类型换算（OffsetDateTime/ZonedDateTime 用系统时区偏移包装同一时刻，LocalDateTime 用系统默认区，long 用 epoch 毫秒）。不注入 Clock 抽象——测试用 before/after 时间窗断言足够，注入点等真实需求出现再加。

### D5 原始 `long` 的 0 哨兵
created 为 `long` 时 0 视为"未设置"被填充——与"0 是合法业务时间戳"冲突概率极低，文档明示，不为此加包装类型强制。

## Risks / Trade-offs

- [upsert 近似语义：客户端新建实体带旧 created 会被保留，即便服务端其实是新行] → 契约与限制清单明确标注"created = 空值填充，不判定服务端存在性"；这是能力边界而非 bug。
- [modified 时间窗内无严格推进（时钟粒度）] → 测试断言"落在新窗口内 + created 不变"，不断言两次 modified 不等。
- [record 重建对带副作用 compact constructor 的实体] → 文档要求幂等校验；极端场景（构造器抛非对称异常）由用户自担，spec 不承诺。
- [反射写回对 JPMS 强封装] → 与既有 idValue 同一前提（实体类在应用 classpath），不新增风险面。

## Migration Plan

纯新增注解与内部钩子；未标注实体零行为变化。回滚 = revert，无迁移。
