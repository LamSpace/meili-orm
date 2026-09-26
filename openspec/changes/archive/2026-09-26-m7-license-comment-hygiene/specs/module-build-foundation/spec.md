# Spec Delta

## ADDED Requirements

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
