# autoconfigure-client Specification

## Purpose

定义 meili-orm 的 Spring Boot 客户端层自动配置契约：`meili.*` 配置属性、连接信息抽象、SDK 客户端扩展点与条件装配/back-off 语义，使用户仅凭配置或同名 bean 即可完整接管连接建立。
## Requirements
### Requirement: meili.* 配置属性集

系统 SHALL 在 `meili.` 前缀下提供配置属性：`enabled`（默认 `true`）、`url`（默认 `http://localhost:7700`）、`api-key`（默认空）、`wait-task`（默认 `false`）、`wait-timeout`（默认 `5s`）、`index.auto-init`（默认 `create-if-missing`）、`index.on-settings-drift`（默认 `warn`）、`client-agents`（字符串列表，默认单元素 `meili-orm`）。属性 SHALL NOT 包含连接/读超时项（SDK 限制）。

#### Scenario: 属性绑定生效

- **WHEN** 应用配置 `meili.url=http://example:1` 与 `meili.api-key=k1` 并启动上下文
- **THEN** 产出的 SDK Client 使用上述 URL 与密钥（可经构建前 Config 观测），其余属性取默认值

#### Scenario: 非法枚举值拒绝

- **WHEN** `meili.index.on-settings-drift` 配置为非 warn/apply/fail 的值
- **THEN** 属性绑定失败，应用启动报错

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

### Requirement: meili.enabled 总开关

`meili.enabled=false` 时，meili-orm 的自动配置 SHALL 不产出任何 bean（Client 与数据层整体退避）；SDK Client 类不在 classpath 时，客户端自动配置 SHALL 静默跳过。

#### Scenario: 禁用后零 bean

- **WHEN** 设置 `meili.enabled=false` 并启动上下文
- **THEN** 容器中不存在 SDK Client、连接详情、operations 等任何 meili-orm 管理的 bean

### Requirement: 用户 Client bean 整体替换

用户声明自有 `com.meilisearch.sdk.Client` bean 时，自动配置 SHALL back-off，不再创建 Client；下游数据层 SHALL 使用用户提供的 Client。

#### Scenario: 用户 Client 获胜

- **WHEN** 上下文已存在用户定义的 Client bean
- **THEN** 自动配置不再产出 Client bean，数据层装配正常完成并注入该用户 bean

### Requirement: 连接信息抽象与替换

系统 SHALL 以 `MeiliConnectionDetails` 接口承载连接信息（URL、API key）：默认由 `meili.*` 属性供给实现；用户（含未来 Testcontainers 服务连接）声明该类型 bean 时，属性实现 SHALL back-off，Client 改用用户 bean 提供的连接信息。

#### Scenario: 用户 ConnectionDetails 优先

- **WHEN** 用户声明返回 `http://manual:9` / `mk` 的 MeiliConnectionDetails bean 且同时存在 `meili.url` 属性
- **THEN** 构建前 Config 观测到 hostUrl 为 `http://manual:9`、apiKey 为 `mk`

### Requirement: Config 扩展点

系统 SHALL 提供 `MeiliConfigCustomizer` 函数式接口：容器中所有该类型 bean 在 SDK Client 构建前按有序流逐个作用于构建前 Config。

#### Scenario: 多个 customizer 依次调用

- **WHEN** 注册两个 MeiliConfigCustomizer bean 并启动上下文
- **THEN** 两者均被调用且作用于同一个构建前 Config，Client 正常产出

### Requirement: 默认 JSON 处理保持哨兵

自动配置产出的 SDK Client SHALL 保持 SDK 默认 JSON 处理器（GsonJsonHandler）：实体读路径契约建立在 raw 字符串通道上，SDK typed 读 API 依赖默认处理器的适配器注册。该约束 SHALL 由常驻装配线哨兵测试锁定（断言构建前 Config 的 jsonHandler 类型为默认实现）。

#### Scenario: 装配线哨兵通过

- **WHEN** 运行客户端自动配置的 L2 测试套件
- **THEN** 存在一条断言：经 customizer 捕获的构建前 Config 其 jsonHandler 为 GsonJsonHandler 实例
