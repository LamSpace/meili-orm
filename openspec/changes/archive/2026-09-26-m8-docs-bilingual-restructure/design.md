# Design

## Context

见 proposal.md - Why。现状事实：交付文档 7 份全中文（README、examples/README、docs 下 5 份指南）；过程材料 4 份散落（仓库根设计文档、`docs/superpowers/plans/` 实施计划、`docs/spikes.md`、`docs/upstream-okhttp-injection.md`）；仓库 public、CI workflow 名 `verify.yml`（master push + PR）；构件未发布（1.0-SNAPSHOT，无 distributionManagement）；`starter-documentation` 主 spec 现以"中文文档集"为契约，且其 README 需求"链接其余三份指南"已落后于文档集现状（实为四份 + examples）。

## Goals / Non-Goals

**Goals:**

- 外部用户从 README 两跳内到达任意所需信息；按文档命令从零可装可跑（源码构建路径）。
- 中英两版逐节对应，锚点（条号/行序）恒一致；交付面与过程面物理分离。

**Non-Goals:**

- 不引入文档站框架（Docusaurus/MkDocs 等）——仓库内 markdown 足够本期规模；不做机器翻译或翻译流水线；不改代码、构建、`openspec/` 规格本体（Purpose 修订在归档同步时按流程处理）；不做 Central 发布。

## Decisions

### D1 镜像布局：根级文件相邻后缀、指南子目录

根级交付文件（README/CONTRIBUTING/CHANGELOG）用相邻 `*.zh-CN.md`；`docs/` 指南镜像统一入 `docs/zh-CN/` 同名子路径。理由：根级一级可见、访客必然看到语言切换；`docs/` 若用后缀则 5 份指南翻倍为 10 个平铺文件且"镜像对"关系靠命名约定隐式维持，子目录使目录结构本身即契约、批量链接检查按目录排除 internal/zh-CN 即可。备选：全后缀式（平铺噪声）、全子目录式（README 失去根级镜像，GitHub 访客易漏）。

### D2 徽章五项定案

| 徽章 | 形态 | 目标链接 |
|---|---|---|
| License: Apache-2.0 | shields 静态（blue） | `/LICENSE` |
| CI: verify | GitHub Actions 实时徽章（public 仓库匿名可访问，已确认） | workflow `verify.yml` 状态页 |
| Java 17+ | shields 静态（orange） | 无（或 CONTRIBUTING 基线段） |
| Spring Boot 3.5.x \| 4.x | shields 静态（green） | boot3-to-boot4 指南 |
| Meilisearch v1.x | shields 静态（品牌粉） | 官方文档锚 |

Maven Central / Javadoc 托管 / release 徽章一律缺席至真实发布；发布时在 CHANGELOG 记徽章追加义务。

### D3 `docs/internal/` 组织：原文件名平铺 + 目录声明

四份材料 `git mv`（保历史）至 `docs/internal/`，文件名保留（含日期前缀）；目录 `README.md` 一句声明"非用户交付材料：设计过程、实施计划、实证档案与上游草稿留档"。不并入 `openspec/`（该树由 CLI 管理且语义是规格而非修订史）。交付文档中指向 spike 的引用改为自洽语句（"实测锁定，哨兵 `SpikeAJsonHandlerIT`/`SpikeBRawJacksonPrecisionIT` 常驻"），不再跳转 internal。

### D4 翻译保真三规则

① 先重写英文规范文本、再据英文结构成稿中文镜像（防中文式英文；镜像非逐句回译但内容等值）；② 代码块/命令/配置键/表格数值两语逐字一致，唯一允许差异是自然语言与注释性文字；③ 术语统一表随英文首稿沉淀（Starter/Auto-configuration/settings 投影/派生查询/fail-fast 等），两份 README 与全部指南复用。README 功能表在英文重写时按 D5 瘦身（能力名 + 一句话 + 详情链接），中文镜像同步采新结构。

### D5 README 英文结构大纲

`# meili-orm` + 一句话定位 → 徽章行 → TL;DR 段 → 📦 Install（未发布真相 + 源码构建步骤 + 三行配置）→ 🚀 Quick Start（现有 Book 实体+搜索样例保留）→ 🗂 Repository 风格访问（现有样例保留，opt-in 说明前置）→ ✨ Features（瘦表）→ ⚙️ Configuration（属性表保留）→ 🚫 Non-Goals → 📚 Documentation（全部指南链接 + 语言切换）→ 🎮 Demos（examples 指引）→ 🧪 Build & Test（通用命令 + Docker/镜像前置，本机 settings 归 CONTRIBUTING）→ 🤝 Contributing → ⚖️ License。

### D6 远程仓库 About 交付文本（Web 手工应用）

Description：`Spring Data–style Spring Boot starter for Meilisearch: annotation-driven mapping, settings projection, templated operations, repositories. One jar for Boot 3.5.x & 4.x.`
Topics：`meilisearch` `spring-boot` `spring-boot-starter` `spring-data` `java` `search` `full-text-search` `orm` `autoconfiguration` `testcontainers` `jackson` `search-engine`
本机无 `gh` CLI，任务列为手工步骤并留应用痕迹记录。

## Risks / Trade-offs

- [两版内容漂移（后续改英文忘镜像）] → 本期以"逐节对应"编写纪律 + 归档后 code review 约定看守；镜像文件存在性可脚本校验（列入收尾任务），内容等值不做机器门禁（成本>收益）。
- [文件迁移断链] → 收尾任务全仓 md 链接扫描（含 examples/README 被 README 与 boot3-to-boot4 的引用）。
- [README 瘦身丢信息密度] → 现功能表的"贡献者视角细节"不删除而是下沉链接到映射指南/限制清单，README 只留能力名 + 一句人话。
- [源码构建安装验证受 m7 门禁影响（需带新 license 头插件的构建）] → 验证任务置于 m7 完成态之后执行，或本地以当前 HEAD 验证流程形态、终局复跑确认。

## Migration Plan

纯文档/元数据变更：internal 迁移（git mv）→ README 双语 → 指南双语（每份 EN 即镜像随）→ CONTRIBUTING/CHANGELOG → About 手工应用 → 全量链接与扫描收尾。回滚 = revert 对应 commit 组。

## Open Questions

（无。）
