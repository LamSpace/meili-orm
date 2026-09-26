# Spec Delta

## MODIFIED Requirements

### Requirement: 矩阵版本哨兵

两模块的 IT SHALL 各含运行时版本哨兵断言：`SpringBootVersion.getVersion()` 分别以 `3.`、`4.` 开头；repository 启用后追加 `SpringDataPackageVersion.getVersion()` 断言——boot3 侧以 `3.5.` 开头、boot4 侧以 `4.0.` 开头（证明两代 commons 运行时各自成立）；哨兵失败信息 SHALL 可定位为"矩阵版本钉定漂移"而非普通断言失败。

#### Scenario: 钉定漂移即红

- **WHEN** 任一矩阵模块的实际运行 classpath 被意外换为另一代 Boot 或另一代 commons（如 BOM 覆盖顺序失效）
- **THEN** 该模块 IT 因对应版本哨兵断言失败而红，构建不通过，失败信息区分 Boot 漂移与 commons 漂移

### Requirement: 双代装配与 CRUD 往返等价

两模块 SHALL 运行内容一致（除版本哨兵期望值外逐字节一致）的 starter IT：以 Testcontainers 真机（v1.49.0）启动应用上下文，注入 `MeiliSearchOperations` 与 `MeiliDocumentSerializer`，在 `wait-task=true`、`auto-init=sync-settings`、`on-settings-drift=apply` 配置下完成建索引→save（含 Long 主键 9007199254740993）→findById→search 往返，断言 hits 命中与主键无损；未引入 jackson3 模块时 serializer 实现 SHALL 为 Jackson2 默认实现。两模块 SHALL 各追加内容一致的 Repository IT（同配置真机）：注入声明了派生查询（等值/IN/Between/Containing/OrderBy）与 `@MeiliQuery` 方法的 `MeiliRepository` 子接口，完成 save→派生查询→分页→注解查询→deleteById 往返，断言两代结果逐条一致——同一份 repository 模块字节码在 commons 3.5.13 与 4.0.3 两代运行时下均验证成立。

#### Scenario: 两代往返一致

- **WHEN** 同一 IT 分别以 Boot 3.5.16 与 4.0.3 classpath 执行
- **THEN** 两代结果一致：findById 与 search 均命中原实体，Long 主键逐位无损

#### Scenario: 两代仓库查询一致

- **WHEN** Repository IT 分别以 commons 3.5.13 与 4.0.3 运行时执行
- **THEN** 派生查询、分页与 `@MeiliQuery` 的命中集合与顺序两代一致，且无 `NoSuchMethodError`/`NoSuchFieldError` 类链接失败

#### Scenario: 矩阵红属于证伪信号

- **WHEN** 矩阵出现"运行期找不到编译期 API"类失败
- **THEN** 该失败按阻断缺陷处理：修复或回退引入方，不得通过给单侧模块换依赖树的方式使其变绿
