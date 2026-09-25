# module-build-foundation Specification

## Purpose
定义 meili-orm 仓库的工程骨架契约：聚合模块划分、字节码兼容基线、第三方依赖钉版纪律与构建期门禁，使后续里程碑（M1–M3）在稳定且可判证的构建事实上进行。
## Requirements
### Requirement: 多模块聚合结构

系统 SHALL 提供聚合根 pom（packaging=pom），模块清单为：`meili-orm-core`、`meili-orm-spring-boot-autoconfigure`、`spring-boot-starter-meili-orm`、`meili-orm-serializer-jackson3`、`it`（含 `it/meili-orm-it-boot3`、`it/meili-orm-it-boot4`）、`examples`（含 common 与 boot3/boot4 两 app）；`meili-orm-core` 的编译期依赖 SHALL NOT 含任何 `org.springframework` 坐标。

#### Scenario: 全量构建通过

- **WHEN** 执行 `mvn -s /home/lam/repo/settings.xml clean verify`
- **THEN** 全部模块（含双矩阵与 examples）构建成功（BUILD SUCCESS），无测试编译错误

#### Scenario: core 零 Spring 依赖

- **WHEN** 执行 `mvn dependency:tree -pl meili-orm-core`
- **THEN** 输出中不存在 `org.springframework` 组坐标

### Requirement: 字节码基线 17

所有模块产出的主字节码 SHALL 为 Java 17 兼容（release=17），无论构建 JDK 版本高低。

#### Scenario: 类文件版本核对

- **WHEN** 构建完成后用 `javap -verbose`（或等价手段）检查 core 产出的任一 class 文件
- **THEN** major version 为 61（Java 17）

### Requirement: 传递依赖钉版

根 pom `dependencyManagement` SHALL 将 SDK 传递依赖钉到确定版本：meilisearch-java 0.21.0、okhttp 5.3.2、gson 2.13.2、Jackson 2.21.2（jackson-bom import 先于 Spring Boot 3.5.16 BOM 声明以取得覆盖优先权）。

#### Scenario: 依赖树版本符合钉版

- **WHEN** 执行 `mvn dependency:tree -pl meili-orm-core`
- **THEN** okhttp 解析为 5.3.2、gson 解析为 2.13.2、jackson-databind 解析为 2.21.2、meilisearch-java 解析为 0.21.0

### Requirement: Javadoc 构建门禁

构建 SHALL 配置 maven-javadoc-plugin（`show=private`）：src/main 中任何类、方法、字段（含私有）缺失 Javadoc 时构建失败。

#### Scenario: 缺失 Javadoc 即红

- **WHEN** src/main 源文件中存在无 Javadoc 的私有成员且执行构建
- **THEN** javadoc 检查报错，构建失败

#### Scenario: 骨架自身合规

- **WHEN** M0 完成后的骨架上执行 `mvn clean verify`
- **THEN** 门禁不报错（占位源文件 Javadoc 齐备）

### Requirement: 内部引用门禁

仓库 SHALL 提供 `scripts/check-source-citations.sh`：扫描发布模块 src/main 注释与模块 pom `<description>` 中的内部过程文档引用，命中即非零退出并定位 file:line，支持 `--selftest` 校验模式集。

#### Scenario: 命中内部引用即失败

- **WHEN** 在某个 src/main 注释中放入被禁模式（如设计文档节号引用）后运行脚本
- **THEN** 脚本非零退出并输出命中位置

#### Scenario: 干净源码通过

- **WHEN** 对 M0 完成态的骨架运行脚本及 `--selftest`
- **THEN** 均零退出

### Requirement: 本机 Maven settings 兜底

仓库 SHALL 在本机存在 `.mvn/maven.config`（内容 `--settings /home/lam/repo/settings.xml`），且该文件 SHALL NOT 纳入版本控制。

#### Scenario: 免 -s 本机可用

- **WHEN** 在本机仓库目录执行不带 `-s` 的 `mvn validate`
- **THEN** 构建使用 `/home/lam/repo/settings.xml` 且成功

#### Scenario: 文件不进 git

- **WHEN** 执行 `git status --ignored` 或 `git ls-files`
- **THEN** `.mvn/maven.config` 未被跟踪且在忽略清单中

### Requirement: 验证与示例模块不产出发布物

`it` 与 `examples` 全部模块 SHALL 在本工程构建配置中声明不参与发布（`maven.deploy.skip=true` 或后续发布插件的等价 skip），使发布面恒为 core、autoconfigure、starter、jackson3 四个产品模块；产品模块的 Javadoc 与内部引用门禁对其 src/main 全量生效，示例代码同样受约束。

#### Scenario: 非发布模块标记在册

- **WHEN** 检查 it 与 examples 各模块 pom
- **THEN** 均含发布 skip 声明；产品四模块不含该声明

#### Scenario: 示例源码过门禁

- **WHEN** 根 reactor 构建执行 javadoc 门禁与 `check-source-citations.sh`
- **THEN** examples 的 src/main 源文件全部合规（私有成员 Javadoc 齐备、无内部过程文档引用）

