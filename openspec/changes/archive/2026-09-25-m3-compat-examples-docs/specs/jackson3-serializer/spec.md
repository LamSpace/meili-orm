# Spec Delta

## Purpose

定义可选模块 meili-orm-serializer-jackson3 的契约：Boot 4 用户在 classpath 加入该模块即接管文档序列化器为 Jackson 3 实现，行为与 Jackson2 默认实现逐项等价；模块缺席时对 Boot 3 用户完全无感。

## ADDED Requirements

### Requirement: 独立可选序列化模块

系统 SHALL 提供发布模块 `meili-orm-serializer-jackson3`：依赖 `meili-orm-core`、spring-boot-autoconfigure 与 `tools.jackson.core:jackson-databind`，其 `tools.jackson` 与 Boot 坐标版本由模块自身 dependencyManagement 导入的 Boot 4.0.3 BOM 解析（覆盖父 pom 的 3.5.16 基线）；根 reactor 之外其余全部产品模块的编译 classpath SHALL NOT 含 `tools.jackson` 坐标。

#### Scenario: Jackson3 坐标不泄漏

- **WHEN** 执行 `mvn dependency:tree` 核对 core、autoconfigure、starter、it-boot3 四模块
- **THEN** 均无 `tools.jackson` 组坐标

#### Scenario: 模块自身构建绿

- **WHEN** 根 reactor 执行 `clean verify`
- **THEN** jackson3 模块编译、测试、javadoc 门禁全部通过

### Requirement: 加依赖即接管序列化器

该模块 SHALL 自带 `AutoConfiguration.imports`，其自动配置以"Jackson 3 ObjectMapper 类在 classpath 且容器无用户自定义 `MeiliDocumentSerializer`"为条件、排序先于数据层自动配置：满足时 `MeiliDocumentSerializer` bean 为 Jackson3 实现；用户自行声明 serializer bean 时 SHALL 让位。

#### Scenario: L2 接管生效

- **WHEN** ApplicationContextRunner 仅加载该模块自动配置
- **THEN** `MeiliDocumentSerializer` bean 是 Jackson3 实现

#### Scenario: 用户 bean 优先

- **WHEN** runner 预注册用户自定义 `MeiliDocumentSerializer` bean 后加载该自动配置
- **THEN** 容器保留用户 bean，Jackson3 实现不注册

#### Scenario: Boot4 矩阵 opt-in 验证

- **WHEN** Boot 4 矩阵的 opt-in 模块（测试 classpath 引入 jackson3 模块）以真机上下文运行接管 IT
- **THEN** 注入的 serializer 为 Jackson3 实现且 CRUD 往返仍绿

#### Scenario: 未引入模块侧无感

- **WHEN** it-boot3 与基线 it-boot4 模块（均未引入 jackson3 依赖）运行矩阵 IT
- **THEN** serializer 保持 Jackson2 实现，构建不因该模块存在于 reactor 而红

### Requirement: 与 Jackson2 行为逐项镜像

Jackson3 序列化器 SHALL 满足与默认 Jackson2 实现相同的行为集：`@MeiliField.name` 改名生效、Long 主键（9007199254740993 量级）写读逐位无损、日期以 ISO 字符串（非时间戳）写出、读侧忽略未知属性；两实现的单元测试 SHALL 使用同一断言集。

#### Scenario: 同断言集双实现

- **WHEN** 对 Jackson3 实现运行与 Jackson2 测试同构的断言集
- **THEN** 改名、精度、日期格式、未知键四条全部通过

### Requirement: 容器 ObjectMapper 择优语义一致

该模块自动配置在容器中已有可用 Jackson 3 ObjectMapper bean 时 SHALL 以其为底构建序列化器（copy 语义，不改用户 mapper 全局配置）；无则自建带稳定默认的实例——与 Jackson2 默认实现的择优规则对称。

#### Scenario: 用户 mapper 定制被尊重

- **WHEN** runner 提供带命名策略定制的 Jackson 3 mapper bean 后装配接管
- **THEN** 序列化输出遵循该命名策略与 `@MeiliField.name` 的既有优先级
