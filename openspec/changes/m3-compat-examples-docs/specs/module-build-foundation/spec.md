# Spec Delta

## MODIFIED Requirements

### Requirement: 多模块聚合结构

系统 SHALL 提供聚合根 pom（packaging=pom），模块清单为：`meili-orm-core`、`meili-orm-spring-boot-autoconfigure`、`spring-boot-starter-meili-orm`、`meili-orm-serializer-jackson3`、`it`（含 `it/meili-orm-it-boot3`、`it/meili-orm-it-boot4`）、`examples`（含 common 与 boot3/boot4 两 app）；`meili-orm-core` 的编译期依赖 SHALL NOT 含任何 `org.springframework` 坐标。

#### Scenario: 全量构建通过

- **WHEN** 执行 `mvn -s /home/lam/repo/settings.xml clean verify`
- **THEN** 全部模块（含双矩阵与 examples）构建成功（BUILD SUCCESS），无测试编译错误

#### Scenario: core 零 Spring 依赖

- **WHEN** 执行 `mvn dependency:tree -pl meili-orm-core`
- **THEN** 输出中不存在 `org.springframework` 组坐标

## ADDED Requirements

### Requirement: 验证与示例模块不产出发布物

`it` 与 `examples` 全部模块 SHALL 在本工程构建配置中声明不参与发布（`maven.deploy.skip=true` 或后续发布插件的等价 skip），使发布面恒为 core、autoconfigure、starter、jackson3 四个产品模块；产品模块的 Javadoc 与内部引用门禁对其 src/main 全量生效，示例代码同样受约束。

#### Scenario: 非发布模块标记在册

- **WHEN** 检查 it 与 examples 各模块 pom
- **THEN** 均含发布 skip 声明；产品四模块不含该声明

#### Scenario: 示例源码过门禁

- **WHEN** 根 reactor 构建执行 javadoc 门禁与 `check-source-citations.sh`
- **THEN** examples 的 src/main 源文件全部合规（私有成员 Javadoc 齐备、无内部过程文档引用）
