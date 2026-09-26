# Spec Delta

## MODIFIED Requirements

### Requirement: meili.* 配置属性集

系统 SHALL 在 `meili.` 前缀下提供配置属性：`enabled`（默认 `true`）、`url`（默认 `http://localhost:7700`）、`api-key`（默认空）、`wait-task`（默认 `false`）、`wait-timeout`（默认 `5s`）、`index.auto-init`（默认 `create-if-missing`）、`index.on-settings-drift`（默认 `warn`）、`client-agents`（字符串列表，默认单元素 `meili-orm`）。属性 SHALL NOT 包含连接/读超时项（SDK 限制）。

#### Scenario: 属性绑定生效

- **WHEN** 应用配置 `meili.url=http://example:1` 与 `meili.api-key=k1` 并启动上下文
- **THEN** 产出的 SDK Client 使用上述 URL 与密钥（可经构建前 Config 观测），其余属性取默认值

#### Scenario: 非法枚举值拒绝

- **WHEN** `meili.index.on-settings-drift` 配置为非 warn/apply/fail 的值
- **THEN** 属性绑定失败，应用启动报错

## ADDED Requirements

### Requirement: meili.client-agents User-Agent 扩展

系统 SHALL 将 `meili.client-agents` 的各条目在 SDK 客户端构建期接入连接配置的 User-Agent 组装：最终 `User-Agent` 值 SHALL 为 SDK 自身版本 token 前置、配置条目按声明顺序以 `;` 追加。属性配置为显式空值时，User-Agent SHALL 仅含 SDK 自身版本 token、无分隔符残留。该组装 SHALL NOT 经构建后的头改写实现（即属性生效于客户端构造链路本身）。

#### Scenario: 默认组装含 meili-orm

- **WHEN** 未配置 `meili.client-agents` 启动上下文，观测构建前 Config 的 `User-Agent` 头
- **THEN** 值等于 SDK 公开版本获取接口返回的自身 token 串追加 `;meili-orm`

#### Scenario: 显式空值回退纯 SDK 默认

- **WHEN** 配置 `meili.client-agents=`（空值）并观测构建前 Config
- **THEN** `User-Agent` 恰为 SDK 自身版本 token，无尾部分隔符

#### Scenario: 多条目保序追加

- **WHEN** 配置 `meili.client-agents=a,b`
- **THEN** `User-Agent` 以 SDK 自身版本 token 开头、以 `;a;b` 结尾
