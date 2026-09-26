# Proposal

## Why

面向外部用户审视交付文档集：全链路中文且无 badge/图标，仓库访客第一眼接收不到项目形态；README 给出的 Maven 坐标（`io.github.lamspace:...:1.0-SNAPSHOT`）未发布到 Central，外部用户按文档装配必然解析失败，构建命令又携带本机专属路径（`-s /home/lam/repo/settings.xml`）；设计文档、实施计划、spike 档案、上游 issue 草稿等过程材料与用户文档混放在根目录与 `docs/`——用户无法区分"该读的"与"不必读的"。需要把文档重组为"英文为主、全部双语镜像、按用户任务组织"的可读可用交付面，并把过程材料隔离。

## What Changes

- **README 重写为英文主文档**（根 `README.md`）：badge 行（License Apache-2.0、GitHub Actions CI（仓库 public，真实徽章）、Java 17+、Spring Boot 3.5.x | 4.x、Meilisearch v1.x；**不放 Maven Central 徽章**）、章节图标（emoji，每 H2 一个）、TL;DR 定位句、与官方 SDK / 社区 `spring-data-meilisearch` 的差异一句、**如实标注"未发布到 Maven Central，当前经源码构建安装"**并给出对应步骤、构建命令通用化（`mvn clean verify`，本机 settings 路径降为注释性说明）、Meilisearch 品牌大小写统一（MeiliSearch → Meilisearch）；中文镜像 `README.zh-CN.md`，两份顶部互链。
- **交付指南全部英文化并配中文镜像**：`docs/mapping-guide.md`、`docs/limitations.md`、`docs/boot3-to-boot4.md`、`docs/testcontainers.md`、`examples/README.md` 以英文为规范文本（English canonical），中文镜像置于 `docs/zh-CN/` 同名文件；跨文档引用改为英文正典 + 镜像对随动；限制清单条号（README 交叉引用 11–16、17–18）在两语版本间保持一致。
- **维护者材料与用户材料分离**：`docs/boot3-to-boot4.md` 的"依赖升级检查清单（维护者义务）"整段迁入 `CONTRIBUTING.md`，升级说明只留用户视角的双代兼容与 Jackson3 选择点。
- **过程材料迁入 `docs/internal/`（非交付面）**：根目录 `2026-09-19-meili-orm-starter-design.md`、`docs/superpowers/plans/2026-09-19-meili-orm-m0-m3.md`、`docs/spikes.md`、`docs/upstream-okhttp-injection.md`（留档，不提交上游）；交付文档不再以内部档案作为阅读前提（限制清单对 spike 的引用降为 internal 脚注）。`docs/internal/README.md` 一句话声明其性质。
- **新增仓库级文档**：`CONTRIBUTING.md`（构建命令与前置、双门禁与 license 头门禁说明、注释/消息英文约定、维护者升级清单迁入处）与 `CHANGELOG.md`（Keep a Changelog 格式，自当前未发布态起记）。
- **远程仓库 About 元数据（手工步骤，文本随变更交付）**：Description 与 Topics 定稿文本写入 tasks，由维护者在仓库 Web 设置中应用。
- `starter-documentation` 主 spec 的"中文文档集"语言契约随之改写为"双语（英文规范 + 中文镜像）文档集"。

无 BREAKING：纯文档面；不改代码、配置、坐标。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `starter-documentation`：① 语言与镜像契约反转（英文规范文本 + `README.zh-CN.md` + `docs/zh-CN/` 镜像对）；② README 需求更新（badge 清单、未发布装配真相、通用构建命令、指南链接全集含 testcontainers——现契约"其余三份指南"已过时）；③ 升级说明需求更新（维护者清单迁出）；④ 新增"内部过程材料隔离"与"CONTRIBUTING/CHANGELOG"两条需求；限制清单与映射指南需求文本随语言与路径更新，行为契约（内容覆盖清单）不变。

## Impact

- **文件**：README ×2 重写、5 份指南英文化 + 5 份镜像、4 份材料移动、`docs/internal/` 与 `docs/zh-CN/` 两目录新增、CONTRIBUTING/CHANGELOG 新增。
- **交叉引用**：限制清单条号、指南互链、examples/README 被 README 与 boot3-to-boot4 引用的路径同步。
- **依赖关系**：`CONTRIBUTING.md` 所述 license 头门禁与注释英文约定由 m7-license-comment-hygiene 建立——m8 实施应在 m7 之后（或至少与其门禁文本对齐）。
- **手工步骤**：GitHub About（Description/Topics）在 Web UI 应用，不在仓库文件内。
- 不动：`openspec/` 规格材料本体、代码与构建（归 m7）。
