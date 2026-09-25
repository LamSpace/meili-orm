# Spec Delta

## Purpose

定义 Spring Boot 3.5.16 与 4.0.3 双代编译运行矩阵：同一 starter 装配 IT 在两代 classpath 下各跑一轮并等价通过，作为"单代码库双兼容"架构决策的常驻护栏，使任何误引单代特有 API 的变更在全量构建即暴露。

## ADDED Requirements

### Requirement: 双代矩阵模块

系统 SHALL 提供 `it/meili-orm-it-boot4` 与 `it/meili-orm-it-boot3` 两个 IT 模块：前者 dependencyManagement 导入 `spring-boot-dependencies` 4.0.3，后者导入 3.5.16（子模块 BOM 声明先于继承的父 BOM 取得覆盖）；两模块均依赖当前 reactor 产出的 `spring-boot-starter-meili-orm` 与 `meili-orm-core` test-jar，并在根 `mvn -s /home/lam/repo/settings.xml clean verify` 时各执行一轮。

#### Scenario: 根全量构建含双矩阵

- **WHEN** 在 Docker 与 v1.49.0 镜像可用的环境执行根 pom `clean verify`
- **THEN** it-boot3 与 it-boot4 两模块的 IT 均执行且 BUILD SUCCESS

#### Scenario: 各模块钉住对应代

- **WHEN** 分别执行 `mvn dependency:tree -pl it/meili-orm-it-boot3` 与 `-pl it/meili-orm-it-boot4`
- **THEN** 前者 `org.springframework.boot` 坐标全部解析为 3.5.16，后者全部解析为 4.0.3

### Requirement: 矩阵版本哨兵

两模块的 IT SHALL 各含一条运行时哨兵断言：`SpringBootVersion.getVersion()` 分别以 `3.`、`4.` 开头；哨兵失败信息 SHALL 可定位为"矩阵版本钉定漂移"而非普通断言失败。

#### Scenario: 钉定漂移即红

- **WHEN** 任一矩阵模块的实际运行 classpath 被意外换为另一代 Boot（如 BOM 覆盖顺序失效）
- **THEN** 该模块 IT 因版本哨兵断言失败而红，构建不通过

### Requirement: 双代装配与 CRUD 往返等价

两模块 SHALL 运行内容一致（除版本哨兵期望值外逐字节一致）的 starter IT：以 Testcontainers 真机（v1.49.0）启动应用上下文，注入 `MeiliSearchOperations` 与 `MeiliDocumentSerializer`，在 `wait-task=true`、`auto-init=sync-settings`、`on-settings-drift=apply` 配置下完成建索引→save（含 Long 主键 9007199254740993）→findById→search 往返，断言 hits 命中与主键无损；未引入 jackson3 模块时 serializer 实现 SHALL 为 Jackson2 默认实现。

#### Scenario: 两代往返一致

- **WHEN** 同一 IT 分别以 Boot 3.5.16 与 4.0.3 classpath 执行
- **THEN** 两代结果一致：findById 与 search 均命中原实体，Long 主键逐位无损

#### Scenario: 矩阵红属于证伪信号

- **WHEN** 矩阵出现"运行期找不到编译期 API"类失败
- **THEN** 该失败按阻断缺陷处理：修复或回退引入方，不得通过给单侧模块换依赖树的方式使其变绿

### Requirement: 矩阵源码双份复制

两模块的同名 IT SHALL 以源码复制形态各自持有（非跨代共享 test-jar），差异仅限版本哨兵期望值；变更任一侧矩阵用例时另一侧 MUST 同步。

#### Scenario: 双份内容核对

- **WHEN** 对两模块的 IT 源文件做 diff
- **THEN** 除哨兵期望字符串外无差异
