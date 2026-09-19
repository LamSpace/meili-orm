# Tasks: meili-orm M0 · 地基与风险清零

> 执行蓝本：docs/superpowers/plans/2026-09-19-meili-orm-m0-m3.md Task 1–3（其 Step 级代码片段、修正指令与验证命令为准）；本清单叠加 design.md 的增量决策（D-1…D-8）。每条 mvn 命令一律 `-s /home/lam/repo/settings.xml`。

## 1. Step 0 预检（D-1，先于一切测试代码）

- [x] 1.1 javap 确认 SDK 0.21.0 关键签名：`Config(String,String,JsonHandler)`、`Client.waitForTask(...)`、`Index.rawSearch(SearchRequest)` 与 `Index.getDocument(String)` 的返回类型（classpath 用 `mvn -s /home/lam/repo/settings.xml -q dependency:build-classpath` 于临时工程或 0.21.0 jar 直接 javap），签名清单记入 docs/spikes.md 预检小节
- [x] 1.2 裸连通冒烟：`docker run -d -p 7700:7700 -e MEILI_MASTER_KEY=... -e MEILI_ENV=development getmeili/meilisearch:v1.49.0` → curl /health → 临时 main/javac 片段以 SDK 直连建索引、删索引各一次 → 命令与实际输出记入 docs/spikes.md「M0.2 冒烟记录」（含"本机 IT 硬前置 = Docker + 本地镜像；缺失时失败形态 = ExceptionInInitializerError"声明）→ 停止并清理临时容器

## 2. 多模块骨架与构建基线（对应计划 Task 1）

- [x] 2.1 重写根 pom.xml 为聚合父 pom：modules（core/autoconfigure/starter）、`maven.compiler.release=17`、dependencyManagement 钉版（jackson-bom 2.21.2 import 先于 spring-boot-dependencies 3.5.16，okhttp 5.3.2/gson 2.13.2/meilisearch-java 0.21.0 显式条目）、pluginManagement（surefire/failsafe 3.5.2、jar 3.4.2）+ maven-javadoc-plugin（show=private，绑定构建生命周期，D-5）
- [x] 2.2 创建 `.mvn/maven.config`（`--settings /home/lam/repo/settings.xml`）并加入 `.gitignore`（D-6，不进版本库）
- [x] 2.3 创建 `meili-orm-core/pom.xml`（SDK+jackson-databind+jsr310+okhttp+slf4j；test 依赖 junit/assertj/mockito/testcontainers/logback；failsafe `*IT` 执行；jar-plugin test-jar）与带 Javadoc 的 `package-info.java`
- [x] 2.4 创建 autoconfigure 与 starter 占位 pom + autoconfigure `package-info.java`（带 Javadoc）
- [x] 2.5 创建 `scripts/check-source-citations.sh`（模式集按 CLAUDE.md §5 约定，命中非零退出并定位 file:line，支持 `--selftest`），本地运行两模式均零退出
- [x] 2.6 创建 `docs/spikes.md` 骨架（并入 1.1/1.2 已有记录）
- [x] 2.7 创建 `MeiliContainer.java` 与 `AbstractMeiliIntegrationTest.java`（按计划 Task 1 Step 6 代码，类/常量 Javadoc 齐备；D-3 保持静态块启动）
- [x] 2.8 验证出口：`mvn -s /home/lam/repo/settings.xml -q clean verify` 三模块绿；`dependency:tree -pl meili-orm-core` 显示 okhttp 5.3.2/jackson 2.21.2/gson 2.13.2/meilisearch-java 0.21.0 且无 org.springframework；`javap -verbose` 任一 class major=61；无 `-s` 的 `mvn validate` 本机成功；`git status` 确认 maven.config 未被跟踪
- [x] 2.9 commit（conventional commits，计划 Task 1 Step 7 文案）

## 3. spikeA —— JsonHandler 兼容性实证（对应计划 Task 2，按 D-2 两遍式）

- [x] 3.1 写探测版 `SpikeAJsonHandlerIT`（对照组默认 GsonJsonHandler / 实验组 JacksonJsonHandler，全模型遍历；伪代码占位以 1.1 javap 结果落地，无悬空 TBD）
- [x] 3.2 运行 `mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-core verify -Dit.test=SpikeAJsonHandlerIT`，逐条捕获两组实际通过/失败点（模型、字段、异常原文），本任务不修 SDK 问题
- [x] 3.3 收紧断言：实验组若红改 `assertThatThrownBy` 锁定不兼容行为，两组终态全绿
- [x] 3.4 `docs/spikes.md` 追加「spikeA 结论」（≤1 页），结论行写死：M2 装配 Client 保持默认 GsonJsonHandler，实体读路径走 raw 字符串 API
- [x] 3.5 commit（计划 Task 2 Step 4 文案）

## 4. spikeB —— raw→Jackson Long 精度实证（对应计划 Task 3，按 D-2 两遍式）

- [x] 4.1 写 `SpikeBRawJacksonPrecisionIT`：①对照断言 Gson Map 通道 `id` 变 Double（若 SDK 实测行为不符则如实放宽并记录）；②主路径 raw 字符串→Jackson，`9007199254740993L` 逐位无损；③rawSearch hits 节点 `treeToValue` 同款断言；`.let()` 伪代码行按真实 API 替换
- [x] 4.2 运行 `-Dit.test=SpikeBRawJacksonPrecisionIT` 至绿（主路径 ②③ 必须绿）
- [x] 4.3 `docs/spikes.md` 追加「spikeB 结论」：raw→Jackson 通道 Long/中文/嵌套无损证据 + `rawSearch`/`getDocument(String)` 确切签名记录（M1 引用锚点）
- [x] 4.4 commit（计划 Task 3 Step 3 文案）

## 5. M0 出口核对与闭环

- [x] 5.1 全量回归：`mvn -s /home/lam/repo/settings.xml -q clean verify` 绿（含两哨兵 IT 随 failsafe 执行）；`bash scripts/check-source-citations.sh` 与 `--selftest` 零退出
- [x] 5.2 回写设计文档：§3.4 风险表四行全部变为"实证后处置"确定陈述（超时=接受并记录/JsonHandler=spikeA 定案/okhttp 传递=已钉版/无 git=已落地）；§2.4"非 git 仓库"与 D1 状态修正为已推送 origin；对应 3 份 spec 场景逐项打勾
- [ ] 5.3 `git push` origin master（首推已建立跟踪，此处推 M0 增量）；确认远程分支状态并在收口汇报中记录
- [x] 5.4 commit（docs 回写，"docs: M0 风险表实证回写与冒烟/哨兵结论固化"类文案）
