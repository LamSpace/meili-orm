## Purpose

实体生命周期回调契约：四件套触发时机、按实体类型的匹配规则、注册顺序语义与注册表自身的可观测性。

## ADDED Requirements

### Requirement: 四件套回调接口

系统 SHALL 提供 `BeforeConvertCallback`（写：序列化前，可变实体）、`AfterSaveCallback`（写：写成功后）、`AfterLoadCallback`（读：原始 JSON 到手后、反序列化前，形参为 raw JSON 字符串并返回改写后的字符串）、`AfterConvertCallback`（读：反序列化后）四类泛型回调接口，均继承统一标记接口以便收集。

#### Scenario: AfterLoad 改写原始文档

- **WHEN** 注册的 AfterLoadCallback 将 raw JSON 中的值替换后再返回
- **THEN** 后续反序列化基于改写后的 JSON

### Requirement: 按实体类型匹配、注册序触发

注册表 SHALL 以反射解析回调的泛型实参确定目标实体类型；触发时仅应用与当前实体类型可赋值的回调（父类回调对子类实体生效），同类型多回调按注册顺序链式执行；泛型实参无法解析的注册 SHALL 抛 `MeiliMappingException`。

#### Scenario: 注册序链式执行

- **WHEN** 按序注册两个 BeforeConvertCallback（分别追加 "-1"、"-2"）并触发
- **THEN** 结果文本为 `x-1-2`

#### Scenario: 不匹配类型跳过

- **WHEN** 注册的回调目标类型与实体无关
- **THEN** 该回调不被调用，实体原样返回

#### Scenario: 泛型不可解析拒绝注册

- **WHEN** 以 raw 类型（无泛型实参）实现回调接口注册
- **THEN** 抛 `MeiliMappingException`

### Requirement: 注册表可观测且可空转

注册表 SHALL 提供 `registeredCount()`（M2 自动配置收集断言依赖）与 `none()` 空实例（所有触发点原样返回）；`register` 与触发 SHALL 支持注册期后只读并发访问的安全语义（Javadoc 声明线程模型）。

#### Scenario: 空注册表透传

- **WHEN** 使用 `none()` 触发任一回调点
- **THEN** 输入值原样返回且不抛异常
