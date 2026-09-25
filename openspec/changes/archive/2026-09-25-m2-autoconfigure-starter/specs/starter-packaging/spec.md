# Spec Delta

## Purpose

定义 `spring-boot-starter-meili-orm` 的发布物契约：聚合依赖坐标、自动配置注册文件完整性与配置属性 IDE 元数据在册，保证"加一个依赖即可用"的 starter 语义。

## ADDED Requirements

### Requirement: starter 聚合坐标

`spring-boot-starter-meili-orm` SHALL 以纯聚合 pom 形式依赖 Boot 基础 starter、`meili-orm-core` 与 `meili-orm-spring-boot-autoconfigure`，自身不含源码；用户仅声明该坐标即可获得全部运行期类与自动配置。

#### Scenario: 单依赖引全栈

- **WHEN** 应用 pom 仅声明 spring-boot-starter-meili-orm 坐标
- **THEN** classpath 含 core、autoconfigure、SDK 传递依赖与 Boot 基础件，上下文可装配

### Requirement: AutoConfiguration.imports 注册完整性

autoconfigure 制品 SHALL 携带 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，逐行列出客户端层、数据层、索引初始化三个自动配置类，每行列出的类 SHALL 可在该制品中加载；`meili.enabled=false` 或条件不满足时注册本身 SHALL 不阻碍上下文启动。

#### Scenario: 三行注册可加载

- **WHEN** 读取制品内 imports 文件并逐行 Class.forName
- **THEN** 恰有三个自动配置类且全部加载成功

### Requirement: 配置属性元数据在册

autoconfigure 制品 SHALL 生成 `META-INF/spring-configuration-metadata.json`：`meili.` 前缀全部属性在册，`index.auto-init` 与 `index.on-settings-drift` 含枚举值提示（values 列举供 IDE 补全）。

#### Scenario: 属性与 hints 完整

- **WHEN** 检查构建产物的配置元数据
- **THEN** enabled/url/api-key/wait-task/wait-timeout/index.auto-init/index.on-settings-drift 全部在册，两个枚举属性带值提示
