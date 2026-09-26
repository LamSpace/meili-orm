# Design

## Context

见 proposal.md - Why。现状事实：176 个 Java 文件（main 81 / test 95）零 License 头；中文注释分布于 main 13 文件（~95 行）与 test 83 文件（~524 行）；中文运行期消息集中在 core/repository/autoconfigure 的异常与日志（~146 行）；全部 pom 的 XML 注释与 `<description>` 为中文；`check-source-citations.sh` 门禁与 javadoc 完整度门禁已常驻构建，翻译不得触碰其模式集。构建 JDK 25、字节码 17，根 reactor 经 CI 全量 verify。

## Goals / Non-Goals

**Goals:**

- License 头成为构建事实：缺头即红，与 javadoc 门禁、引用门禁同级。
- 全仓本文源（Java 注释、pom/yml 文本、main 运行期消息）英文单语；中文仅存于显式枚举的数据白名单。
- 根 pom 元数据达到"看一眼即知许可证与出处"的开源仓库基线。

**Non-Goals:**

- 不动 `README.md`、`docs/`、`openspec/`（语言与重组归 m8）；不引入 spotless/格式化重排；不做 Central 发布配置（gpg/central-publishing 均不加）；不改任何公共 API、异常类型、消息中的定位信息结构。

## Decisions

### D1 头部文本：仓库内单一模板文件、字面量无占位符

`LICENSE` 附录形态的短头部（版权行 `Copyright 2026 the original author or authors.`，`/* ... */` 块）存为仓库单一模板文件（如 `etc/license-header.txt`），全文 ASCII、无属性占位符。备选自带模板 + `${owner}/${year}` 属性：占位符引入配置面且措辞不由己——固定年份与措辞恰是本期契约，选字面量。

### D2 门禁实现：com.mycila:license-maven-plugin 钉 4.x

`check` 目标绑定默认生命周期早期阶段（validate），随根 `clean verify` 与 CI 执行；文件集 `<includes>**/*.java</includes>`，`.java` 用 `SLASHSTAR_STYLE`（块注释）映射；插件版本钉定于根 `pluginManagement`（依赖钉版纪律延伸），"实施时最新稳定"条款落点 = **com.mycila:license-maven-plugin:5.1.2**（2026-09 查证 Maven Central latest，实施记录见 evidence/scan-baseline.md）。备选：自研 grep 脚本（重复造轮子且无 format 插入器）、google 旧 license 插件（停更）。批量插入不手写脚本——用同插件 `format` 目标一次执行，保证插头格式与 `check` 判定严格一致。

### D3 执行顺序：先翻译、后插头

内容翻译（消息→注释→pom/yml 文本）完成并测试全绿后再 `license:format`，使 176 文件的头部插入成为独立、可复核的一次机械 diff；插头后跑全门禁收尾。顺序颠倒亦可但会把同一批文件改两遍、diff 互相污染。

### D4 翻译策略三条红线

① 运行期消息只换语言不换语义：保留 `{}` 占位符与嵌入的类名/字段名/文件名/方法名定位信息，异常类型与抛出条件不动；② 断言中文消息子串的测试与消息同步改为英文断言（消息非契约，见 spec）；③ 数据白名单显式枚举进 tasks 的核验清单（spike IT 的 `三体`/`刘慈欣` 样本、examples 书目数据与 `data.json`、golden 透传资源等），白名单外零中文豁免。

### D5 验证以扫描命令为准

"注释零中文"与"main 非注释零中文（白名单外）"各给可复制 grep（注释行模式 `^\s*(//|\*|/\*)`；pom/yml 全文模式），CI 不加常驻 grep 门禁（`check-source-citations.sh` 管"内部引用"不管语言；语言约定经 CLAUDE.md §5 补一句人肉看守 + code review），头部门禁常驻、语言约定半常驻——这是本期最简一致选择。

### D6 根 pom 元数据落点

`<licenses>/<scm>/<url>/<developers>` 全部只在根 pom 声明，子模块继承；scm url 用 `https://github.com/LamSpace/meili-orm` 公开形态（非 SSH 个人地址）。

## Risks / Trade-offs

- [`license:format` 在特殊文件插错位（`package-info.java` 注解先行、含 BOM/既有首块注释的文件）] → 插头后 `git diff --stat` 核对文件数恰为 176 + 构建门禁 + 抽查全部 package-info；javadoc 门禁会连带暴露位置错误导致的注释失效。
- [消息翻译破坏既有断言 → 测试变红面不可预估] → 先 `grep -rlP '[\x{4e00}-\x{9fff}]' src/test` 圈定含中文断言文件清单，逐文件改后跑该模块测试再进下一个。
- [新插件坐标在离线/镜像缺失] → 本地 `/home/lam/repo/settings.xml` 镜像与 CI 均可联网拉取；拉取失败即 D2 钉版记录回滚（插件改动独立成 commit，可单独 revert）。
- [翻译量大（约 1100 行注释 + 146 行消息）易漏] → 以"扫描命令零命中"为出口判据，不依赖人工清点。
- [根聚合 pom 目录无 Java 文件，插件空文件集执行] → 确认 mycila 空集 `check` 为通过（如非，根模块 `<skip>` 置真、子模块继承生效），实施时以一次构建验证裁定。

## Migration Plan

全部分步为独立可 revert 的 commit：翻译（按模块分组）→ pom 元数据 + 插件配置 → `license:format` 插头 → 门禁收尾。无需数据/接口迁移；对外行为变化面 = 异常/日志消息语言（README/docs 已在 m8 同步为英文语境）。

## Open Questions

（无——署名年份与措辞、双语分工、门禁方式均已由维护者拍板，见 proposal 决策记录。）
