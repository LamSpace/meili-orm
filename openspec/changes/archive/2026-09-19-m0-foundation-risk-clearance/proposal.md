# Proposal: meili-orm M0 · 地基与风险清零

## Why

设计文档（2026-09-19）§3.4 风险表中"自定义 JsonHandler 是否兼容 SDK 内部模型"与"raw→Jackson 读路径 Long 精度是否无损"两项仍是**未知**——M1 的全部实现建立在这两项的结论之上，未实证就动工等于把架构风险后移。同时仓库仍是单模块 `source/target=25` 空骨架，依赖纪律（release=17、传递依赖钉版、双代兼容编译护栏的前提）尚不存在。M0 的使命是把地基浇筑和未知清零一次做完，让 M1–M3 只面对"实现"而不面对"未知"。

## What Changes

- 仓库重构为多模块聚合：`meili-orm-core`（零 Spring）、`meili-orm-spring-boot-autoconfigure`、`spring-boot-starter-meili-orm`（占位），字节码基线 `maven.compiler.release=17`（构建 JDK 仍 25）。
- 根 pom `dependencyManagement` 钉版：meilisearch-java 0.21.0、okhttp 5.3.2、gson 2.13.2、jackson-bom 2.21.2（先于 Boot 3.5.16 BOM 声明以覆盖其钉版）、surefire/failsafe 3.5.2、jar 3.4.2；`dependency:tree` 作为钉版的可验证出口。
- 构建门禁落地：`maven-javadoc-plugin`（`show=private`）配置进聚合构建，新建 `scripts/check-source-citations.sh` 内部文档引用门禁——使 CLAUDE.md §5 的"已配置"声明与仓库现实一致（本期决策）。
- Testcontainers 集成基建：`MeiliContainer`（钉 `getmeili/meilisearch:v1.49.0`、master key、`/health` 等待策略）与 `AbstractMeiliIntegrationTest`，随 core **test-jar** 供 M1–M3 各模块复用。
- spikeA/spikeB 实证（真容器 IT）：①JsonHandler 注入下 SDK 内部模型解析兼容性；②raw 字符串→Jackson 通道 Long/中文/嵌套精度。结论 ≤1 页/条写入 `docs/spikes.md`，并以**收紧后的断言固化为常驻哨兵测试**（不是一次性脚本）。
- M0.2 裸连通冒烟承接：Step 0 预检（javap 三个 SDK 关键签名 + `docker run` 起 v1.49.0 直连建删索引一次），输出记录进 `docs/spikes.md`（实施计划原稿中该子任务无归属，本变更将其落为预检动作而非新测试类）。
- 闭环回写：`docs/spikes.md` 结论同步更新设计文档 §3.4 风险表"实证后处置"列，并修正 §2.4/§3.4 中"非 git 仓库/D1 待决"两条已失效事实（git 已 init 并推送 origin）。
- `.mvn/maven.config`（`--settings /home/lam/repo/settings.xml`，机器专属）**不进 git**，加入 `.gitignore`；`-s` 约定继续由 CLAUDE.md/README 显式承载（本期决策）。

不改动：M1+ 的 core 主源码（注解/元模型/Operations 等）不在本变更范围；不动 git 历史；不新增业务功能。

## Capabilities

### New Capabilities

- `module-build-foundation`: 多模块工程结构、字节码基线、依赖钉版纪律与构建门禁（javadoc 私有能力检查、内部引用扫描）——"mvn clean verify 全绿 + dependency:tree 版本符合钉版"是可判证契约。
- `meili-it-infrastructure`: MeiliSearch v1.49.0 Testcontainers 单例容器与 IT 基类的跨模块复用（经 core test-jar 分发），Docker 为声明式硬前置。
- `sdk-read-path-evidence`: 读写通道架构决策的实证基础——spikeA 定案 Client 装配保留默认 GsonJsonHandler、spikeB 定案实体读路径唯一通道为 raw JSON 字符串→Jackson；两项结论以常驻哨兵 IT 防 SDK/服务端升级回归。

### Modified Capabilities

（无——`openspec/specs/` 尚无主规格，本变更为首批 capability。）

## Impact

- **文件**：重写根 `pom.xml`；新增三个模块 pom 与 `package-info.java` 占位；新增 `.mvn/maven.config`（gitignored）、`scripts/check-source-citations.sh`、`docs/spikes.md`；新增 `meili-orm-core/src/test/.../{MeiliContainer,AbstractMeiliIntegrationTest,spike/SpikeAJsonHandlerIT,spike/SpikeBRawJacksonPrecisionIT}.java`；修改 `.gitignore`、设计文档 §2.4/§3.4。
- **依赖**：本地 `~/.m2`→`/home/lam/repo` 首次全量拉取 Boot 3.5.16 BOM 树、meilisearch-java 0.21.0、okhttp 5.3.2、jackson 2.21.2、testcontainers（已实测 aliyun 镜像全部 200 可达）；离线环境构建将失败，属声明式前置。
- **下游**：Task 12（Client 装配）、Task 5/6/9（raw 通道契约）以本变更 spikeA/spikeB 结论为实现约束；Task 14/16/18 复用 test-jar 基建。
- **运行环境**：Docker 守护进程与本地 v1.49.0 镜像是 IT 的硬前提（镜像已在本地，251MB，无需拉取）。
