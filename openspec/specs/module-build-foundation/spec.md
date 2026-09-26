# module-build-foundation Specification

## Purpose
定义 meili-orm 仓库的工程骨架契约：聚合模块划分、字节码兼容基线、第三方依赖钉版纪律与构建期门禁，使后续里程碑（M1–M3）在稳定且可判证的构建事实上进行。
## Requirements

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

`it` 与 `examples` 全部模块 SHALL 在本工程构建配置中声明不参与发布（`maven.deploy.skip=true` 或后续发布插件的等价 skip），使发布面恒为 core、autoconfigure、starter、jackson3、repository 五个产品模块；产品模块的 Javadoc 与内部引用门禁对其 src/main 全量生效，示例代码同样受约束。

#### Scenario: 非发布模块标记在册

- **WHEN** 检查 it 与 examples 各模块 pom
- **THEN** 均含发布 skip 声明；产品五模块不含该声明

#### Scenario: 示例源码过门禁

- **WHEN** 根 reactor 构建执行 javadoc 门禁与 `check-source-citations.sh`
- **THEN** examples 的 src/main 源文件全部合规（私有成员 Javadoc 齐备、无内部过程文档引用）

### Requirement: commons 隔离构建护栏

core、autoconfigure、starter 三模块 SHALL 配置 maven-enforcer `bannedDependencies`（禁 `org.springframework.data:*`），使"spring-data-commons 仅存在于 repository 模块"的隔离纪律成为构建事实；`meili-orm-repository` 与 it/examples 模块不受该 ban 约束（it 模块经 repository 依赖合法引入 commons）。

#### Scenario: 违规引入即构建失败

- **WHEN** 任一受约束模块的依赖树出现 `org.springframework.data:*`（如误给 starter 加 repository 依赖）
- **THEN** 该模块构建在 enforcer 规则处失败，输出可定位为被 ban 坐标

#### Scenario: 正常 reactor 全绿

- **WHEN** 当前依赖拓扑执行根 reactor `clean verify`
- **THEN** enforcer 规则全部通过

### Requirement: CI 持续集成验证流水线

仓库 SHALL 提供持续集成流水线：默认分支推送与拉取请求触发，在具备 Docker 容器执行能力的运行环境中执行全 reactor 构建验证（含双代兼容矩阵模块与服务端集成测试）以及内部引用门禁脚本及其自检模式。流水线配置与其引用的构建设置文件 SHALL NOT 含本机专属绝对路径或私有凭据，且 SHALL 以显式方式指定 Maven settings 文件。

#### Scenario: 推送触发全量验证

- **WHEN** 向默认分支推送提交或开启拉取请求
- **THEN** 流水线执行全量 `clean verify`（含双代矩阵与服务端 IT）与内部引用门禁及自检模式复跑，通过与否以构建结论呈现

#### Scenario: 无本机专属依赖

- **WHEN** 审查流水线配置与其引用的 settings 文件内容
- **THEN** 不存在指向特定开发机绝对路径（如用户主目录下的仓库目录）的引用，不存在凭据信息

### Requirement: Java 源文件 License 头与构建门禁

仓库内全部模块的每个 Java 源文件（`src/main` 与 `src/test`，含 examples 与 it）SHALL 携带 Apache-2.0 短头部注释，版权声明行 SHALL 为 `Copyright 2026 the original author or authors.`，其后至 `limitations under the License.` 的许可引用段与仓库 `LICENSE` 文件 Apache 附录形态一致。根构建 SHALL 将"头部完整性检查"绑定进默认验证生命周期（随 `clean verify` 执行，覆盖全部模块的 `**/*.java`）：任一文件缺失头部或头部与规定文本不符即构建失败；检查通过与否 SHALL NOT 依赖人工评审。

#### Scenario: 缺头或错头即构建失败

- **WHEN** 在任一模块的 `src/main` 或 `src/test` 下新建一个无 License 头的 Java 文件（或篡改既有文件的版权行）后执行根 reactor `clean verify`
- **THEN** 构建在头部检查处失败，输出可定位为被违反的文件

#### Scenario: 全量文件合规通过

- **WHEN** 对 m7 完成态执行根 reactor `clean verify`
- **THEN** 头部检查对全部 Java 文件（含新增文件与 `package-info.java`）零违规通过

### Requirement: 源码语言为英文

全部文本源文件中的**注释与说明性文本** SHALL 为英文：各模块 `src/main` 与 `src/test` 的 Java 注释/Javadoc（含 `package-info.java` 与 examples）、根与各模块 pom 的 XML 注释与 `<description>`、示例配置（yml）的注释。`src/main` 中面向用户的**运行期文本**（异常消息、日志输出文案） SHALL 为英文；消息文本 SHALL NOT 作为断言契约——既有对中文消息子串的测试断言随翻译更新为英文形态，异常类型、消息所承载的定位信息（类名/字段名/文件名/方法名）与触发条件不变。承担测试/演示**数据**语义的中文字符串字面量（CJK 无损验证的样本值、示例书籍数据、golden 资源内容等） SHALL 保留中文，且豁免清单 SHALL 以显式枚举（文件 + 用途）形式记录在变更实施材料中，不设隐式豁免。

#### Scenario: 注释与运行期文本零中文

- **WHEN** 对完成态仓库执行扫描：Java 注释行（`//`、`*`、`/*` 起始）匹配 CJK 字符，及 `src/main` 非注释行与 pom/yml 文本匹配 CJK 字符（豁免清单除外）
- **THEN** 全部扫描零命中（`docs/`、`README*`、`openspec/` 材料不在本需求范围内）

#### Scenario: 数据字面量按白名单保留

- **WHEN** 核对豁免清单（如 core spike IT 的 `三体`/`刘慈欣` 样本、examples 的中文书目数据与 `data.json`、golden 透传资源）
- **THEN** 清单内中文数据在源码/资源中保持原样，且对应哨兵测试（CJK 往返精度断言）仍全绿

### Requirement: 根 pom 开源元数据

根 pom SHALL 声明开源项目元数据：`<licenses>`（Apache-2.0，含 name/url/distribution/comments）、`<scm>`（指向公开仓库的 connection/developerConnection/url）、`<url>`、`<developers>`（维护者标识），子模块 SHALL 经继承获得而 SHALL NOT 在各自 pom 重复声明。元数据 SHALL 与仓库实际状态一致（许可证与 `LICENSE` 文件一致、SCM 地址即当前 origin 对应的公开仓库）。

#### Scenario: effective-pom 元数据在册

- **WHEN** 对任一产品模块执行 `mvn help:effective-pom`
- **THEN** 输出的 pom 含上述 licenses/scm/url/developers 段且值与仓库公开事实一致
