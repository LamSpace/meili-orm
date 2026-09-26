# Spec Delta

## MODIFIED Requirements

### Requirement: 多模块聚合结构

系统 SHALL 提供聚合根 pom（packaging=pom），模块清单为：`meili-orm-core`、`meili-orm-spring-boot-autoconfigure`、`spring-boot-starter-meili-orm`、`meili-orm-serializer-jackson3`、`meili-orm-repository`、`it`（含 `it/meili-orm-it-boot3`、`it/meili-orm-it-boot4`）、`examples`（含 common 与 boot3/boot4 两 app）；`meili-orm-core` 的编译期依赖 SHALL NOT 含任何 `org.springframework` 坐标；`meili-orm-repository` SHALL 依赖 `meili-orm-core` 与 `meili-orm-spring-boot-autoconfigure`，其编译期 `spring-data-commons` 版本 SHALL 由 Boot 3.5.16 BOM 管理的 3.5.13 解析（最低支持代编译基线），且 SHALL NOT 被 `spring-boot-starter-meili-orm` 聚合传递。

#### Scenario: 全量构建通过

- **WHEN** 执行 `mvn -s /home/lam/repo/settings.xml clean verify`
- **THEN** 全部模块（含 repository、双矩阵与 examples）构建成功（BUILD SUCCESS），无测试编译错误

#### Scenario: core 零 Spring 依赖

- **WHEN** 执行 `mvn dependency:tree -pl meili-orm-core`
- **THEN** 输出中不存在 `org.springframework` 组坐标

#### Scenario: starter 不传递 commons

- **WHEN** 执行 `mvn dependency:tree -pl spring-boot-starter-meili-orm`
- **THEN** 输出不含 `meili-orm-repository` 与 `org.springframework.data:*` 坐标

### Requirement: 验证与示例模块不产出发布物

`it` 与 `examples` 全部模块 SHALL 在本工程构建配置中声明不参与发布（`maven.deploy.skip=true` 或后续发布插件的等价 skip），使发布面恒为 core、autoconfigure、starter、jackson3、repository 五个产品模块；产品模块的 Javadoc 与内部引用门禁对其 src/main 全量生效，示例代码同样受约束。

#### Scenario: 非发布模块标记在册

- **WHEN** 检查 it 与 examples 各模块 pom
- **THEN** 均含发布 skip 声明；产品五模块不含该声明

#### Scenario: 示例源码过门禁

- **WHEN** 根 reactor 构建执行 javadoc 门禁与 `check-source-citations.sh`
- **THEN** examples 的 src/main 源文件全部合规（私有成员 Javadoc 齐备、无内部过程文档引用）

## ADDED Requirements

### Requirement: commons 隔离构建护栏

core、autoconfigure、starter 三模块 SHALL 配置 maven-enforcer `bannedDependencies`（禁 `org.springframework.data:*`），使"spring-data-commons 仅存在于 repository 模块"的隔离纪律成为构建事实；`meili-orm-repository` 与 it/examples 模块不受该 ban 约束（it 模块经 repository 依赖合法引入 commons）。

#### Scenario: 违规引入即构建失败

- **WHEN** 任一受约束模块的依赖树出现 `org.springframework.data:*`（如误给 starter 加 repository 依赖）
- **THEN** 该模块构建在 enforcer 规则处失败，输出可定位为被 ban 坐标

#### Scenario: 正常 reactor 全绿

- **WHEN** 当前依赖拓扑执行根 reactor `clean verify`
- **THEN** enforcer 规则全部通过
