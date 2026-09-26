# starter-documentation Specification

## Purpose

定义首版发布候选的交付文档集契约：以"英文规范文本 + 中文镜像"成对组织的 README、映射指南、限制清单、Boot 3→4 升级说明、Testcontainers 集成、examples 指南与 CONTRIBUTING/CHANGELOG，只转录已实现且已验证的行为，命令可复制执行，承诺不超出实现；过程材料隔离于 `docs/internal/`，交付面不以其为阅读前提。

## Requirements

### Requirement: README 装配与功能面

根 `README.md`（英文规范文本）与其镜像 `README.zh-CN.md` SHALL 含：starter Maven 坐标与最小装配（yml 三行级示例）与未发布状态下的源码构建说明（见"README 徽章与未发布真相"）、一个实体+搜索代码样例、功能表（对应设计基线目标集；Repository 层与 Testcontainers 层作为独立坐标可选能力在功能表与装配小节中如实标注——各含"不在 starter 聚合内、需显式加对应依赖"的一句话说明）、一个仓库接口+派生查询代码样例、demo 一键运行流程转录、构建命令与 Docker/镜像前置声明（命令以通用形态书写，不携带本机专属绝对路径；本机 settings 差异归 CONTRIBUTING 说明）；并链接其余**全部**交付指南（映射指南、限制清单、Boot 3→4 升级说明、Testcontainers 集成、examples 说明）与中文镜像。服务端品牌拼写在英文文本中 SHALL 采用官方形态 Meilisearch。

#### Scenario: 命令零臆造

- **WHEN** 逐条核对 README 中出现的构建/docker/curl 命令
- **THEN** 每条均为矩阵或 demo 真机执行过的原文转录（或其后继等价形态），且不含本机专属路径

#### Scenario: Repository 装配面准确

- **WHEN** 按 README 的 repository 小节从零搭建最小工程
- **THEN** 仅按文档声明的坐标与配置即可获得可注入的 `MeiliRepository`，无需查阅其他材料

### Requirement: 限制清单完整且不超售

`docs/limitations.md`（英文规范文本）与其镜像 `docs/zh-CN/limitations.md` 与 README 限制小节 SHALL 逐项覆盖已实证限制并各给 workaround：连接/读超时不可配（SDK 硬约束）、`count` 走 stats 端点的语义、multiSearch v1 串行、Boot4 默认无 Jackson2 容器 mapper 时的自建行为、写后可查须 `wait-task`、改 filterable/sortable 触发全量重建、非目标表（无响应式、无 @Version、无 per-field analyzer、无 SpEL 动态索引名）；Repository 面 SHALL 追加：`Page.getTotalElements()` 为估算值、`findAll()` 受 maxTotalHits 截断、Containing/Like 为全文近似（非子串语义）、`deleteAll(Iterable)` 逐条请求、派生查询关键字支持子集与启动期角色预检（透传声明不进入预检输入）、无 DTO 投影/Stream/异步返回形态；实体审计面第 17–18 条维持。文档不得出现任何未实现承诺；两语版本条目编号与分组一致；内部实证档案不作为阅读前提（自洽承载，见"内部过程材料隔离"）。

#### Scenario: 逐项对照核销

- **WHEN** 评审文档限制清单与实现/测试逐条对照
- **THEN** 每项限制有对应代码事实或哨兵测试依据，无超出实现范围的功能描述

#### Scenario: 条号交叉引用两语同解

- **WHEN** 在英文与中文 README 中分别解析"第 11–16 条""第 17–18 条"引用
- **THEN** 两份语言版本均解析到同一内容分组，无错位

### Requirement: 映射指南可独立教会注解

`docs/mapping-guide.md`（英文规范文本）与其镜像 `docs/zh-CN/mapping-guide.md` SHALL 含：每个注解一行的"注解→Meilisearch 概念/settings"对照表、"不标注=不声明"铁律及其后果（displayed 白名单效应）、嵌套点路径规则、`@MeiliSetting` 透传与投影的合并/覆盖优先级、`searchableOrder` 排序语义、生命周期回调四件套样例、settings 投影管线图、审计字段章（`@CreatedDate`/`@LastModifiedDate` 填充语义、六类型许可集、写回双形态与近似语义警示）；并含派生查询章：方法名关键字→MeiliQuery 行为对照表（支持/不支持两个清单，与实现报错面逐字一致）、投影名桥接规则（`@MeiliField.name` 与点路径如何出现在生成的 filter 中）、`@MeiliQuery` 模板语法与参数绑定/转义规则、角色预检的修复指引示例。

#### Scenario: 对照表与实现一致

- **WHEN** 按指南对照表构造实体并核对投影输出（对照 golden 测试）
- **THEN** 指南描述与实际投影行为一致

#### Scenario: 派生查询表可复算

- **WHEN** 按派生查询章的任一对照行写方法名并运行 L1 翻译断言
- **THEN** 生成的 filter/q/sort 与指南给出的目标串一致；列入"不支持"清单的方法名确实启动报错

### Requirement: 升级说明与依赖升级检查清单

用户面升级说明 `docs/boot3-to-boot4.md`（英文规范文本）与其镜像 `docs/zh-CN/boot3-to-boot4.md` SHALL 含：单代码库编译基线策略（autoconfigure 编译取 3.5.x 代、repository 编译取 commons 对应代、jackson3 模块为唯一取 Boot 4 BOM 的产品模块之例外声明）、双矩阵工作方式与 Docker 前置、双代行为差异表与 jackson3 可选模块用法、repository 模块的 commons 两代运行期兼容面说明；其**用户视角自洽**——升级决策所需信息不依赖维护者义务材料。**依赖升级检查清单**（维护者义务：变更任一钉版后必须根 reactor 全绿（含双矩阵与哨兵）、重读读写通道哨兵结论是否仍成立、做一轮 demo 真机冒烟）SHALL 迁至 `CONTRIBUTING.md` 承载，升级说明内不再出现该小节。

#### Scenario: 升级说明用户面自洽

- **WHEN** 以 Boot 3→4 升级者视角通读升级说明
- **THEN** 换/不换坐标、是否引入 jackson3 的决策所需信息完整，且不含维护者义务内容

#### Scenario: 升级义务书面化

- **WHEN** 查阅 CONTRIBUTING 的清单小节
- **THEN** 清单逐项给出可执行的验证命令与判定标准，覆盖 SDK/Boot/commons/服务端四类钉版变更

### Requirement: 双语文档集（英文规范 + 中文镜像）

交付文档 SHALL 全部以"英文规范文本 + 中文镜像"成对组织：根 `README.md` ↔ `README.zh-CN.md`；`docs/` 下各指南（映射指南、限制清单、Boot 3→4 升级说明、Testcontainers 集成）↔ `docs/zh-CN/` 同名文件；`examples/README.md` ↔ `examples/README.zh-CN.md`；根 `CONTRIBUTING.md` ↔ `CONTRIBUTING.zh-CN.md`；`CHANGELOG.md` ↔ `CHANGELOG.zh-CN.md`。镜像对 SHALL 逐节对应：代码块的可执行内容（命令、配置键、API 名、标识符与字面量取值）与表结构两语逐字一致，仅自然语言行文不同；代码块内注释与表格说明性文字属自然语言，随版本语言翻译；文内跨文档链接 SHALL 指向同语言版本，每份文档顶部 SHALL 含指向另一语言版的切换链接；限制条目编号、功能表行序、配置表内容 SHALL 两语一致。

#### Scenario: 镜像对完整性

- **WHEN** 枚举交付集全部英文文档（`docs/internal/` 豁免）
- **THEN** 每份在约定中文位置存在同名镜像文件，反向亦成立，无孤儿单语文件

#### Scenario: 两语锚点一致

- **WHEN** 对照任一语言的条目编号/功能表行序/配置表与其镜像版本
- **THEN** 编号与行序逐条一致，README 对限制清单的条号交叉引用在两份 README 中解析到同一内容块

### Requirement: README 徽章与未发布真相

`README.md` 与其镜像顶部 SHALL 含徽章行，恰为五项：License Apache-2.0（链接 `LICENSE`）、CI 状态（指向本仓库真实 workflow，公开仓库匿名可访问）、Java 17+ 字节码基线、Spring Boot 3.5.x 与 4.x 双代、Meilisearch v1.x 服务端兼容；除 CI 徽章外 SHALL 以静态徽章呈现且语义与实现事实一致。构件未发布至公共仓库期间，README SHALL NOT 含 Maven Central 版本类徽章或任何暗示坐标可远程解析的表述；装配小节 SHALL 明示"尚未发布到 Maven Central"并给出源码构建安装步骤（clone → 根 `mvn install` → 本地解析坐标）与其适用边界。

#### Scenario: 幻影徽章缺席

- **WHEN** 在构件未发布状态下检查两份 README 的徽章与装配小节
- **THEN** 不存在 Central/Javadoc 托管/下载量类徽章链接，装配小节含未发布声明与源码构建路径

#### Scenario: 源码构建安装实测走通

- **WHEN** 干净环境（非维护者本机）严格按 README 源码构建步骤安装后按最小装配建工程编译
- **THEN** `spring-boot-starter-meili-orm` 经本地仓库解析成功，工程编译通过

### Requirement: 内部过程材料隔离

非交付过程材料 SHALL 集中于 `docs/internal/`：初始设计文档、实施计划、spike 实证档案、上游 issue 草稿（仅留档不提交），目录内 `README.md` 一句话声明其"非用户交付面"性质；上述材料 SHALL NOT 散落于仓库根或 `docs/` 交付面。交付集全部文档 SHALL NOT 以 `docs/internal/` 材料为阅读前提、SHALL NOT 出现内部过程代号引用（设计文档节号、里程碑/任务代号等）；原引用处（如限制清单对 spike 记录的指针）以自洽语句承载——实测结论与哨兵测试名可在交付文档内直接成立，无需跳转。

#### Scenario: 交付文档无内部依赖

- **WHEN** 对交付集（`README*`、`docs/` 除 `internal/`、`examples/README*`）扫描 `docs/internal`、`superpowers`、`spikes.md` 及内部代号模式引用
- **THEN** 零命中（哨兵测试类名作为代码事实的引用不在此列）

#### Scenario: 迁移完整且历史保留

- **WHEN** 检查根目录、`docs/superpowers/` 与四份材料原路径
- **THEN** 原位置文件不存在、`docs/internal/` 下齐备，git 历史可追溯（重命名用 `git mv`）

### Requirement: CONTRIBUTING 与 CHANGELOG

仓库 SHALL 提供 `CONTRIBUTING.md`（英文规范 + 相邻中文镜像），含：构建命令与前置（Docker/镜像、本机 Maven settings 差异说明——绝对路径仅出现于此）、三道构建门禁（Javadoc 完整度、内部引用、License 头）与违规表现、源码语言约定（注释/运行期消息英文、中文数据白名单机制）、维护者依赖升级检查清单（自升级说明迁入，覆盖 meilisearch-java、两代 Boot、spring-data-commons、服务端镜像四类钉版变更，逐项给出可执行验证命令与判定标准）。`CHANGELOG.md`（英文规范 + 相邻中文镜像）SHALL 按 Keep a Changelog 格式维护，自当前未发布态起记录值得用户感知的变更。

#### Scenario: 命令零臆造可复制

- **WHEN** 在本机按 CONTRIBUTING 给出的构建/验证命令逐条执行
- **THEN** 每条按文档原文可执行，结论（全绿/门禁报错）与文档描述一致

#### Scenario: 升级清单覆盖四类钉版且落点唯一

- **WHEN** 查阅 CONTRIBUTING 清单小节并比对升级说明（boot3-to-boot4）
- **THEN** 四类钉版变更逐项有命令与判定标准；升级说明内不再出现维护者义务小节
