# Spec Delta

## ADDED Requirements

### Requirement: CI 持续集成验证流水线

仓库 SHALL 提供持续集成流水线：默认分支推送与拉取请求触发，在具备 Docker 容器执行能力的运行环境中执行全 reactor 构建验证（含双代兼容矩阵模块与服务端集成测试）以及内部引用门禁脚本及其自检模式。流水线配置与其引用的构建设置文件 SHALL NOT 含本机专属绝对路径或私有凭据，且 SHALL 以显式方式指定 Maven settings 文件。

#### Scenario: 推送触发全量验证

- **WHEN** 向默认分支推送提交或开启拉取请求
- **THEN** 流水线执行全量 `clean verify`（含双代矩阵与服务端 IT）并随后执行内部引用门禁与 `--selftest`，通过与否以构建结论呈现

#### Scenario: 无本机专属依赖

- **WHEN** 审查流水线配置与其引用的 settings 文件内容
- **THEN** 不存在指向特定开发机绝对路径（如用户主目录下的仓库目录）的引用，不存在凭据信息
