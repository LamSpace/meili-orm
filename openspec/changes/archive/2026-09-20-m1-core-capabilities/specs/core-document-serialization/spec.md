## Purpose

实体 ↔ 文档 JSON 的可插拔序列化契约：两方法 SPI 面、Jackson2 默认实现的精度/改名/日期行为，以及容器注入 ObjectMapper 的自定义语义。

## ADDED Requirements

### Requirement: 序列化 SPI 仅两方法

`MeiliDocumentSerializer` SHALL 只暴露 `String write(Object)` 与 `<T> T read(String, Class<T>)`，签名中不出现任何 Jackson/JSON 树类型，使任意格式引擎（Jackson3 等）可实现整体接管而不改调用方。

#### Scenario: 接口面冻结

- **WHEN** 审查 `MeiliDocumentSerializer` 的公开签名
- **THEN** 有且仅有 write/read 两方法，参数/返回类型均为 String/Object/Class

### Requirement: Long 主键无损往返

默认实现 SHALL 对超过 2^53 的 long 值 `write→read` 逐位无损（不经任何 `Number→Double` 中间形态），中文与嵌套结构无损。

#### Scenario: 大数主键往返

- **WHEN** 序列化再反序列化 `id=9007199254740993` 的 record 实体
- **THEN** 读出值与原值逐位相等，序列化产物文本含字面量 `9007199254740993`

### Requirement: 改名与排除和元模型一致

默认实现 SHALL 使 `@MeiliField.name` 的改名在序列化与反序列化双向生效（与映射层投影名同一规则源），`@JsonIgnore` 字段不出现在产物中。

#### Scenario: 改名双向生效

- **WHEN** 实体字段 `title` 标注 `@MeiliField(name="book_title")`
- **THEN** write 产物含 `"book_title"` 且不含 `"title"` 键；read 同文档得到的实体 `title` 值正确

### Requirement: 日期 ISO 化与宽容读取

默认实现 SHALL 输出 JSR-310 日期为 ISO-8601 字符串（非数字时间戳），读取时 SHALL 忽略文档中未知的额外属性；转换失败 SHALL 包装为 `MeiliOrmException` 并携带目标类型信息。

#### Scenario: OffsetDateTime ISO

- **WHEN** 序列化含 `OffsetDateTime` 字段的实体
- **THEN** JSON 中该值形如 `2008-01-01T00:00:00Z`，不含浮点时间戳

#### Scenario: 未知属性容忍

- **WHEN** read 的 JSON 含实体不存在的额外键
- **THEN** 正常构造实体，不抛异常

### Requirement: 尊重注入的 ObjectMapper 配置

构造默认实现时传入的 base ObjectMapper SHALL 被复制后叠加固定默认（注解桥接、jsr310、宽容读、ISO 日期）：调用方的命名策略等自定义生效，且调用方原 mapper 不被修改。

#### Scenario: SNAKE_CASE 策略生效

- **WHEN** 以 SNAKE_CASE 命名策略的 mapper 构造序列化器并 write 字段 `unitPrice`
- **THEN** 产物含 `unit_price`；同一 mapper 后续独立使用不受影响
