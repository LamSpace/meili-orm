# Proposal

## Why

仓库已在 GitHub 公开（`LamSpace/meili-orm`），但源码尚未完成开源化整理：176 个 Java 源文件无一含 License 头、根 pom 亦无许可证元数据；源码注释（main 约 95 行 / test 约 524 行）与 main 中约 146 行运行期消息（异常文案、日志输出）仍为中文——外部贡献者读不懂契约，英文用户在 stack trace 与日志里看到中文排障信息。这些应成为构建期门禁固化的构建事实，而非一次性清理后无人看守。

## What Changes

- 全部 Java 源文件（`src/main` + `src/test`，176 个）加 Apache-2.0 短头部，署名行按 `Copyright 2026 the original author or authors.` 形态；经 `license-maven-plugin`（com.mycila，钉 5.1.2）`check` 目标常驻门禁：缺头/错头即构建失败，批量插入用其 `format` 目标完成。
- 源码语言统一为英文：
  - main 与 test 的全部注释/Javadoc 译为英文（含 `package-info.java`、examples）；
  - main 中的异常消息与日志文案（约 146 行，core/repository/autoconfigure 为主）译为英文——消息非契约，但断言中文消息子串的用例须同步更新；
  - **中文测试/演示数据字符串保留**（CJK 无损往返验证等哨兵的语义载体，如 `三体`/`刘慈欣`），数据白名单显式列出。
- 非 Java 文本源同步英文化：根/各模块 pom 的 XML 注释与 `<description>`（现均为中文）、示例 `application.yml` 注释；内部引用门禁（`check-source-citations.sh`）保持全绿。
- 根 pom 补开源元数据：`<licenses>`（Apache-2.0）、`<scm>`、`<url>`、`<developers>`。
- 项目指令 `CLAUDE.md` §5 增补"注释语言为英文"约定一句（与门禁互证）。

无 BREAKING：不改公共 API、签名、行为分支；异常/日志消息语言变化不构成契约变更（消息文本非断言契约，受影响测试随译）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `module-build-foundation`：新增三条需求——① Java 源文件 License 头与 license 插件构建门禁；② 源码语言策略（注释/Javadoc/运行期消息英文，中文数据字面量豁免）；③ 根 pom 开源元数据（licenses/scm/url/developers）。

## Impact

- **代码**：`src/{main,test}/**/*.java` 全量加头；main 39 个含 CJK 文件与 test 83 个含 CJK 注释文件翻译；断言中文消息的测试同步。
- **构建**：根 pom 新增 license-maven-plugin（钉版）与 pom 元数据；门禁入根 `clean verify`，CI（`verify.yml`）自动覆盖。
- **依赖**：新增仅构建期插件坐标（com.mycila:license-maven-plugin），不进任何产物 classpath。
- **不动**：`docs/` 与 `README.md` 的语言与重组归另一变更（m8-docs-bilingual-restructure）；`openspec/` 材料；发布坐标（仍 1.0-SNAPSHOT，暂不发布 Central）。
