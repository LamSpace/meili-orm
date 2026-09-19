## Context

见 proposal.md · Why。当前仓库状态：单模块 pom（source/target=25）、`src/` 空骨架、git 已 init 并推送 `origin/master`、OpenSpec 已初始化。环境实测（本变更侦查阶段完成）：JDK 25.0.3 + Maven 3.9.16；本地仓库已缓存 Boot 4.0.3 但 **M0 所需坐标（meilisearch-java 0.21.0 / okhttp 5.3.2 / gson 2.13.2 / jackson 2.21.2 / Boot 3.5.16 / testcontainers / surefire 3.5.2）均未缓存**，已逐项 HEAD 探测 aliyun 镜像全部 200——首次构建是一次纯下载，非连通性风险；`getmeili/meilisearch:v1.49.0` 镜像本地已有。

实施计划（docs/superpowers/plans/2026-09-19-meili-orm-m0-m3.md）Task 1–3 是本变更的执行蓝本；本设计文档只记录在其之上做出的**增量决策**与计划文本中已知占位（伪代码行）的处置原则。

## Goals / Non-Goals

**Goals:**
- 满足三份 spec 的全部场景（构建事实、IT 基建、实证结论）。
- spikeA/spikeB 的产出物 = 收紧后的哨兵 IT + ≤1 页书面结论 + 风险表回写，三者缺一不可。

**Non-Goals:**
- 不写任何 core 主源码逻辑（注解、元模型、Operations 均归 M1 的变更）。
- 不做 Testcontainers reuse/Dynamic Container Registry 优化——镜像本地已有，单例静态容器够用。
- 不解决 SDK 上游缺陷（超时不可注入仅记录，向 SDK 提 issue 是 M3 文档期的动作）。

## Decisions

### D-1 Step 0 预检先于任何测试代码（新增于计划之上）

**做法**：Task 1 动工前执行——① `javap` 确认 `Config(String,String,JsonHandler)`、`Client.waitForTask`、`Index.rawSearch/getDocument(String)` 的 0.21.0 实际签名；② `docker run` 裸起 v1.49.0，SDK 直连建删索引一次，输出记入 spikes.md（即设计文档 M0.2"裸连通冒烟"，计划原稿中无归属，本变更将其落为预检动作而非测试类）。
**理由**：计划 Task 2/3 的代码片段含占位伪代码（`Constants.HOST_URL_FALLBACK`、`.let()`），计划自身规定"以 javap 实测为准修正"。先测量，spike 的观察项才只剩真正的未知（handler 行为、精度行为），避免"签名写错"污染"兼容性未知"的记录。
**备选**：按计划原样从 Task 1 直接开跑——被否，首轮失败归因成本转嫁给 spikes 记录环节。

### D-2 spike 采用特征化测试而非 TDD 红绿

**做法**：两遍式——第一遍宽松探测（实验组允许抛错，捕获原文），第二遍按实测收紧断言：成功则断言成功，失败则 `assertThatThrownBy` 锁定异常形态。
**理由**：spike 断言的是"世界是什么样"而非"我们希望的行为"；锁定坏行为（Gson Map 通道精度受损）与锁定好行为同等重要——都是未来 SDK 升级的回归防线。
**备选**：一次性脚本跑完即弃——被否，失去常驻哨兵价值（spec `sdk-read-path-evidence` 明确要求纳入 verify 生命周期）。

### D-3 `MeiliContainer` 保留计划原设计：静态块启动、public static 单例

**理由**：test-jar 消费方（Task 14/16/18）零配置复用；v1.49.0 镜像本地已有，启动成本≈容器冷启动数秒；it-boot3/it-boot4 各自 JVM 各起一个容器可接受。
**已知代价（接受并记录）**：Docker 不可用时抛 `ExceptionInInitializerError`，错误形态不友好——通过 spikes.md 冒烟记录声明"Docker 为硬前置 + 失败长什么样"来消化（对应 spec 场景"缺 Docker 时失败形态可辨识"）；误加载类的单测会付容器启动成本——M0 无单测，M1 若成问题再改 lazy。
**备选**：`@Container` 注解式 + 手动启动、或 JUnit extension 封装——对 M0 都是过度工程。

### D-4 BOM import 顺序即钉版策略：显式条目 + jackson-bom 先于 Boot BOM

**做法**：按计划 Task 1 的根 pom 结构，jackson-bom 2.21.2 import 置于 spring-boot-dependencies 3.5.16 之前；okhttp/gson/meilisearch-java 以显式 dependencyManagement 条目钉版。出口以 `dependency:tree` 实测为准，若 jackson 非 2.21.x 则调整顺序直至生效（计划原文规定）。
**理由**：Maven 对冲突的 managed 版本"先声明者胜"，这是零插件成本的确定性机制；Boot 3.5.16 仅作编译基线 BOM（autoconfigure 依赖最低支持代），非运行期承诺。
**备选**：`maven-enforcer-plugin` managedPlugins/dependencies 规则强校验——推迟到 M1+ 视需要再加，M0 以 dependency:tree 人工核对为出口。

### D-5 门禁落点：javadoc 插件进聚合 build，引用扫描脚本进 scripts/

**做法**：`maven-javadoc-plugin`（show=private，绑定 package/verify 阶段，仅 src/main 生效）配置于根 pom build；`scripts/check-source-citations.sh` 按计划外新建立即存在的门禁脚本，含 `--selftest`。M0 期 src/main 只有两个 `package-info.java`（带 Javadoc），门禁成本为零，但 CLAUDE.md §5 从此与仓库现实一致。
**理由**：M0 名为"地基"，门禁属于地基的一部分（用户已拍板）。
**备选**：推迟到 M1 首次出现真实主源码时——被否，会产生"CLAUDE.md 与现实矛盾"的中间窗口。
**执行期修正（实证）**：javadoc doclet 无法处理"仅 package-info.java、零类型"的模块（`error: No public or protected classes found to document`，JDK 25 javadoc 实测，`--ignore-source-errors` 不存在于该版本）。经用户拍板：根 pom 保持完整门禁配置（show=private + doclint=missing + failOnWarnings），core 与 autoconfigure 两模块临时 `<skip>true</skip>` 并在 pom 注释标明解除条件（首个真实主源码类落地，即 M1 首个主源码任务）；缺注释致红的机制（failOnWarnings）已探测确认（doclint warning → 构建失败）。引用门禁脚本不受此限，M0 当日即全量生效。

### D-6 `.mvn/maven.config` 本机生效、版本库排除

**做法**：文件写入工作区并加入 `.gitignore`；`-s /home/lam/repo/settings.xml` 约定继续由 CLAUDE.md 与（M3 的）README 显式承载，计划内所有 mvn 命令保持显式 `-s`。
**理由**：个人绝对路径提交进已公开的 GitHub 仓库会污染 clone/CI（用户已拍板）。
**备选**：提交进仓库（计划原文倾向）——被否。

### D-7 版本微调：surefire/failsafe 维持 3.5.2

本地仓库缓存的是 3.5.4，但镜像有 3.5.2（已探测 200），按计划钉 3.5.2 以保持计划文本一致性；下载成本一次性。

### D-8 闭环回写路径

spikes.md（一手证据）→ 设计文档 §3.4"实证后处置"列 + §2.4/§3.4 失效事实修正（git 部分）→ M1 实现契约锚点由 Task 12 的注释与哨兵 IT 常驻承载（不在本变更内改动 M1 任务文本）。每 Task 一个 conventional commit，M0 收口 `git push`。

## Risks / Trade-offs

- [首轮构建下载量大（Boot 3.5.16 全树 + SDK + TC），网络抖动可致半途失败] → 已实测 aliyun 全部 200；失败按计划在 Task 1 Step 7 停下报告，不改回默认 settings。可先 `dependency:resolve` 预热。
- [spikeA 实验组行为可能介于"全绿/全红"之间（部分模型解析异常）] → 两遍式（D-2）按实际粒度记录，结论行仍收敛为"保持默认 GsonJsonHandler"单一处置，不随观察浮动。
- [SDK 0.21.0 的 `rawSearch`/`getDocument(String)` 返回类型与计划假设不符] → D-1 预检 javap 前置暴露，修正签名不算偏离计划（计划明文）。
- [javadoc 插件 show=private 对 record/注解等将来结构可能过严] → M0 范围内无暴露面；M1 若需豁免（如 lombok 风格豁免）届时在插件 exclude 层面处理，不预先放宽。
- [静态容器使 `mvn verify` 依赖 Docker，纯 CI 单测场景变慢/变红] → M0 接受（本机开发为主）；IT 与单测已通过 surefire/failsafe 分离，必要时将来以 `-DskipITs` 逃逸。

## Migration Plan

无部署物；纯仓库内变更。回滚 = git revert 对应 commit（Task 边界即 revert 边界）。

## Open Questions

- 远程默认分支命名（master vs main）与是否推送每次 commit——不影响 spec/任务拆解，收口时确认即可。
