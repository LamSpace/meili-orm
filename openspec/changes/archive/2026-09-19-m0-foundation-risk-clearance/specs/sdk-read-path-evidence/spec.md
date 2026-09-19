## Purpose

以真机实证固化 meili-orm 读写通道的两条架构契约——SDK 内部 JsonHandler 的兼容性边界、raw JSON→Jackson 通道的数值精度无损性——并让证据以常驻哨兵测试与书面结论的形式持续生效。

## ADDED Requirements

### Requirement: spikeA 定案 JsonHandler 兼容边界

系统 SHALL 产出一项真机实证：对照默认 `GsonJsonHandler` 与注入自定义 JsonHandler 两种装配，遍历 SDK 内部模型（settings、任务、文档读取等 typed 便捷 API）的解析行为；结论 SHALL 以"哪个模型、哪个字段、何种异常"的粒度记入 `docs/spikes.md`（≤1 页），并显式写明对下游 Client 装配的处置（保持默认 handler 或可安全替换）。

#### Scenario: 对照组行为基线

- **WHEN** 以默认 handler 的客户端执行 写入→等待任务→读 settings 全链
- **THEN** 各模型解析成功，结论记录在案

#### Scenario: 实验组行为按实测锁定

- **WHEN** 以自定义 handler 的客户端执行同一全链
- **THEN** 无论解析成功或抛出特定异常，哨兵测试以与实际一致的断言固化该行为（成功则断言成功，失败则锁定异常类型与消息）

### Requirement: spikeB 定案 raw 通道精度无损

系统 SHALL 产出一项真机实证：含 64 位大整数主键（>2^53）的文档经 写入→raw 字符串读取→Jackson 反序列化 后 SHALL 与原值逐位相等；同时以对照断言锁定"经 SDK 内部 Map 通道同一主键精度受损"的事实，作为读路径绕开该通道的持续理由。中文与嵌套字段 SHALL 一并往返无损。

#### Scenario: 大整数主键往返无损

- **WHEN** 保存 `id = 9007199254740993` 的文档并经 raw→Jackson 通道读回
- **THEN** 读回值与写入值精确相等

#### Scenario: 坏通道行为被锁定而非回避

- **WHEN** 同一文档经 SDK 内部 Map 便捷 API 读取
- **THEN** 哨兵测试按实测行为（精度受损）断言之，行为变化即测试变红

### Requirement: 哨兵测试常驻构建生命周期

spikeA/spikeB 的测试 SHALL 以 `*IT` 命名纳入 failsafe 生命周期，随每次 `mvn verify` 执行，作为 SDK 或服务端版本升级时的兼容性回归防线，不得在实证完成后删除。

#### Scenario: verify 触发哨兵

- **WHEN** 在 core 模块执行 `mvn clean verify`
- **THEN** 两个哨兵 IT 均运行且通过（收紧后断言）

### Requirement: 结论回写风险表

`docs/spikes.md` 的结论 SHALL 回写设计文档 §3.4 风险表：对应行从"未知/待实证"变为"实证后处置"，全表不得残留未知项；已失效的环境事实（非 git 仓库、D1 待决）SHALL 一并修正。

#### Scenario: 风险表无未知项

- **WHEN** 审阅 M0 收口后的设计文档 §3.4
- **THEN** 每行"应对/处置"均为已实证的确定陈述
