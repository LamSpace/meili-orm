# Tasks

## 1. 骨架与过程材料迁移

- [x] 1.1 `git mv` 四份过程材料入 `docs/internal/`（根设计文档、`docs/superpowers/plans/…m0-m3.md`、`docs/spikes.md`、`docs/upstream-okhttp-injection.md`，保留原文件名），新建 `docs/internal/README.md` 一句声明非交付面，verify：原路径不存在、internal 下齐备、`git log --follow` 历史可追溯
- [x] 1.2 建立镜像骨架：`docs/zh-CN/` 目录；verify：`ls docs/` 下无交付文档滞留 internal、目录空位就绪
- [x] 1.3 应用远程仓库 About（手工，Web UI）：Description 与 Topics 按 design D6 定稿文本设置，verify：仓库页 About 显示描述与 topics 列表（已应用——GitHub API 实测 description 逐字=D6 文本、topics=12 项全数在册）

## 2. README 双语重写

- [x] 2.1 重写英文 `README.md`：五徽章行（design D2）、D5 结构大纲、TL;DR 与定位句、未发布装配真相（源码构建步骤）、Repository/Testcontainers 双 opt-in 说明、功能表瘦身（每行能力名+一句话+详情链接）、命令通用化（无本机绝对路径）、Meilisearch 大小写统一、顶部中文镜像链接；verify：徽章五项 href 逐一可访问（curl 全 200）、无 Central 类徽章、文档命令与 examples/README 真机转录一致（2026-09-26 复录）
- [x] 2.2 成稿中文镜像 `README.zh-CN.md`（按 2.1 英文结构重写而非旧中文版保留，逐节对应），verify：两版标题/表行序/条号交叉引用逐节对齐、顶部互链可达

## 3. 指南双语（每份：英文规范文本 + docs/zh-CN/ 镜像）

- [x] 3.1 `limitations.md`：英文化（spike 引用改自洽语句含哨兵测试名、去本机路径、大小写统一），中文镜像同稿随出；verify：18 条编号与 README 两语版"第 11–16/17–18 条"引用同解（EN/ZH 均 1–18 连续、分组标题行号 69/109 对称）
- [x] 3.2 `mapping-guide.md`：英文化 + 中文镜像；verify：抽取派生查询对照表 In/Between/Containing 三行，L1 断言组 MeiliDerivedQueriesTest 24/24 绿；顺带修正原文事实错误（白名单 19 项→代码实测 18 项，两语同步）
- [x] 3.3 `boot3-to-boot4.md`：拆分——用户面英文化（差异表、jackson3 用法、commons 兼容面）+ 中文镜像；维护者"依赖升级检查清单"整段移交 4.1 的 CONTRIBUTING 承载；verify：指南内不再出现维护者义务小节（84 行用户面；清单已迁 4.1）
- [x] 3.4 `testcontainers.md`：英文化 + 中文镜像；verify：注解桥接与手工桥接样例代码与模块 IT 一致（执行体对 MeiliSearchContainer/退避守卫源码交叉核对）、命令可复制
- [x] 3.5 `examples/README.md`：英文化（一键流程去本机路径）+ `examples/README.zh-CN.md`；curl 输出以现状复录一轮真机转录（若与 2026-09-25 记录一致则原文保留并注日期）；verify：导入/检索/单读/删除四场景命令逐条可执行（2026-09-26 boot4+boot3 双壳真机复录，英文日志与漂移 WARN 均实录转录）

## 4. 仓库级文档（依赖 m7 门禁落地后引用其事实）

- [x] 4.1 `CONTRIBUTING.md`（英文）+ `CONTRIBUTING.zh-CN.md`：构建命令与前置（Docker/镜像/本机 settings——绝对路径唯一允许出现处）、三道门禁（Javadoc 完整度/内部引用/License 头）与违规表现、源码语言约定与数据白名单机制、维护者依赖升级检查清单（SDK/Boot/commons/服务端四类，逐项命令+判定标准）；verify：清单命令逐条本机可执行（A–D 全取自既证实命令）、升级说明无残留
- [x] 4.2 `CHANGELOG.md`（英文，Keep a Changelog）+ `CHANGELOG.zh-CN.md`：Unreleased 段记 m7（License 头+英文源码+元数据）与本变更（双语重组、未发布装配真相），并预留"发布后追加 Central 徽章"义务句；verify：格式符合 keep-a-changelog 结构、两语条目一致

## 5. 集成校验与收尾

- [x] 5.1 交付面全量扫描：`README*`、`docs/`（除 internal）、`examples/README*`、CONTRIBUTING/CHANGELOG 中——`/home/lam` 命中仅限 CONTRIBUTING 两版、`docs/internal` 与 `superpowers` 引用零命中、镜像对存在性逐对核对（无孤儿单语文件）；verify：三条扫描通过——/home/lam 仅 CONTRIBUTING 两版；交付面 docs/internal 引用仅 CONTRIBUTING 的元描述句（非阅读前提）；9/9 镜像对齐全；EN 文档正文（去 fence/code-span/切换行）零汉字
- [x] 5.2 全仓 md 内链/外链逐条可达性检查（迁移与重写后断链排查），verify：无 404/相对路径失效（全相对链脚本核验全通过；两处页面内锚点 #-build--test/#-构建与测试 为 GitHub emoji 标题合法 slug；徽章外链 curl 200）
- [x] 5.3 spec 场景逐条核对：镜像完整性、幻影徽章缺席、源码构建安装实测（干净环境 clone→`mvn -s <自己的 settings> install`→最小装配工程编译通过）、两语锚点一致、升级义务落点唯一；verify：五组判定通过——镜像完整性 9/9、幻影徽章缺席、源码构建安装实测（2026-09-26 复录即走 clone→install→装配路径）、两语锚点一致、升级义务落点唯一
- [x] 5.4 门禁与回归：`bash scripts/check-source-citations.sh` 绿（md 不在门禁内但不得引入新违规引用），根 reactor 不受文档变更影响复确认；verify：脚本零退出（reactor 无文档外输入变化，m7 终局全绿仍成立）
- [x] 5.5 归档注记：本变更归档同步 `starter-documentation` 主 spec 时，其 Purpose 文本（"中文文档集契约"）须一并改为双语集表述（主 spec Purpose 不随 delta 流转，归档时手工修订）；verify：归档后主 spec Purpose 与需求语言契约一致（归档同步时 Purpose 已改为双语集表述，旧"中文文档集契约"字样随 delta 落地）
