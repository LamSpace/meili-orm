# Design

## Context

M0–M2 完成态：core（零 Spring）+ autoconfigure（3 个自动配置类进 imports）+ starter 聚合 pom 全 reactor 绿；spikeA/spikeB 与 core/autoconfigure IT 哨兵常驻；`MeiliQuery.filterAdd`、`MeiliEntityCallbacks.registeredCount` 两处回改已落码。本机 Maven 仓库（/home/lam/repo）已同时缓存 Boot 3.5.16 与 4.0.3 系产物（autoconfigure 以 3.5.16 为编译基线时拉齐），设计文档 §2.4 "it-boot3 首跑联网"的残余风险已大幅降级但传递树完备性仍需首跑证实。

约束（承自全局纪律）：一切 mvn 命令带 `-s /home/lam/repo/settings.xml`；新增 Java 源受 Javadoc（show=private）与 `scripts/check-source-citations.sh` 双门禁；IT 服务端钉 `getmeili/meilisearch:v1.49.0`（Docker 已是既有 verify 的硬前置）。

动机见 proposal.md - Why；行为要求见本 change 的 5 份 spec delta。

## Goals / Non-Goals

**Goals:**
- 把方案 A（单代码库双代兼容）从"4.0.3 侧冒烟过"升级为"两代常驻编译运行矩阵"，成为后续一切变更的自动护栏。
- jackson3 可选模块以最小面积兑现"加依赖即接管"，且证明 Boot3 侧无感。
- demo 以单套业务字节码双壳运行，覆盖 spec §7 全场景并留真机痕迹。
- 文档四件套定义发布候选：只转录已验证事实。

**Non-Goals:**
- 不做 API 签名回归工具（japicmp/Revapi）——尚无已发布版本可比对，首个 release 后再评估。
- 不把 demo 的 REST 行为场景全量自动化进 verify（真机冒烟为出口动作；自动化只到 context-load 层）。
- 不动 core/autoconfigure/starter 的既有公开 API；不启动 M4（Repository）任何内容。
- 不引入 CI 流水线配置（本地 verify 即门禁执行处）。

## Decisions

### D-1 矩阵用例源码双份复制，非 test-jar 共享
两代各持一份同名 IT（仅版本哨兵期望值不同），由 diff 核对逐字节一致。备选 build-helper 共享源目录或跨代 test-jar：前者构建复杂度高，后者要回答"共享 jar 用哪代字节码编译"这个本矩阵正要隔离的问题，且 60 行测试的复制漂移成本近乎为零。防假绿靠版本哨兵：`SpringBootVersion.getVersion()` startsWith "3."/"4."——它同时防"子模块 BOM 覆盖顺序失效导致两侧跑同一代"这种矩阵没钉住版本的静默失败。

### D-2 子模块 BOM 覆盖机制
it-boot4、examples-boot4、jackson3 在各自 pom 的 `dependencyManagement` 首位 import `spring-boot-dependencies:4.0.3`，依赖 Maven"最近声明优先"覆盖继承自父 pom 的 3.5.16 BOM；it-boot3 与 examples-boot3 与父基线同代、无需覆盖但显式写出以对称自证。该顺序敏感性正是哨兵测试保护的对象。

### D-3 jackson3 是全工程唯一编译基线取 Boot 4.0.3 的产品模块
`tools.jackson` 坐标只有 Boot 4 BOM 管理，故该模块必须按 D-2 覆盖。这破了"编译依赖取最低支持代"的普适表述但逻辑自洽：Boot3 用户 classpath 上没有 tools.jackson，模块的 `@ConditionalOnClass` 由 ASM 元数据评估、不触类加载，加入即惰化无操作。此例外连同理由写进 boot3-to-boot4.md（升级说明的既有要求），不是隐患是声明。

### D-4 Jackson3 opt-in IT：独立第三矩阵模块 `it/meili-orm-it-boot4-jackson3`，替代计划稿的 `-Dmeili.jackson3=true` 开关
系统属性不能增删 classpath 条目，计划稿的 opt-in 开关形态不可实现；而把 jackson3 塞进 it-boot4 的测试类路径并要求主 IT 显式 `spring.autoconfigure.exclude`，又会破坏矩阵 spec"双侧 IT 除哨兵行外逐字节一致"的要求（排除属性只属 boot4 侧）。故第三形态：新建 `it-boot4-jackson3` 模块——对 Jackson2 基线 it-boot4 加 test-jar 依赖以复用其 `ITBook`/`ItApp` 测试类（同时以 Boot 4.0.3 + Jackson2 编译基线自证主 IT 断言的 Jackson2 默认形态），自身仅新增一个 opt-in IT：测试 classpath 引入 jackson3 模块，断言 serializer 接管为 Jackson3 且 CRUD 往返绿。"类缺席"分支由 it-boot3 与基线 it-boot4（均不依赖 jackson3 模块）承担，即"Boot3 侧无感"场景；L2 runner 测试覆盖用户 mapper 与 user bean 让位形态。

### D-5 Jackson3 实现用"测试镜像"策略
`Jackson3DocumentSerializerTest` 移植 `Jackson2DocumentSerializerTest` 的同一断言集（`book_title` 改名、9007199254740993 逐位无损、ISO 日期非时间戳、未知键忽略），而非照 tools.jackson API 重新发明测试；`@MeiliField.name` introspector 代码镜像 Task 5 规则（两代 Jackson 注解内省 API 形状相同）。行为等价的验证成本低于实现审查。

### D-6 examples：编译防腐进 reactor，运行验证走手工脚本
- 进根 `<modules>`，`mvn verify` 的 package 阶段兜住"API 改名 demo 红"（编译期腐烂）。
- 每 app 一个零 Docker 的 context-load 冒烟测试（`meili.index.auto-init=none` + 指向未监听端口——Client 构造不发请求已由 M2 L2 实证），兜住装配期腐烂（扫描器/控制器/回调/serializer wiring）。
- 行为期腐烂留给真机 curl 冒烟（出口动作），不自动化：核心场景已被 core/autoconfigure 的 L3 IT 同构覆盖，demo 全自动化等于用双份 Docker 时间买边际覆盖。
- example-common 编译基线 3.5.16（同 autoconfigure 纪律）；Boot4 app 引同一份 common 字节码。
- demo 控制器读 `data.json` 显式 `new com.fasterxml.jackson...ObjectMapper`，不注入容器 mapper——Boot4 容器默认是 Jackson3，注入会当场红，红得有道理但没必要。

### D-7 reactor 扩容与发布面纪律
新增 `it`（聚合+2 模块）、`meili-orm-serializer-jackson3`、`examples`（聚合+common+2 app）进根 modules；it/examples 全部 pom 声明 `maven.deploy.skip=true`，发布面恒为 core/autoconfigure/starter/jackson3 四产品模块。Javadoc 与引用门禁对 examples 的 src/main 生效（controller/entity/config 均需按 CLAUDE.md §5 深度写私有成员 Javadoc），it 模块只有 src/test 不受 javadoc 扫描。

### D-8 文档=已验证事实的转录，验证手段机械化
README/指南中每条命令必须是 T16–T18 实际执行过的原文（docker 启动、mvn 构建、curl 集及其输出摘录），出口核对含"逐条命令来源可指认"检查；限制清单对照设计文档 §3.4/§1.2/§5.2 逐项核销；drift-WARN 的重建代价告警须在 demo 真机观察一次再落笔，不凭单测结论写运行手册。

## Risks / Tradeoffs

- [it-boot3 传递依赖树不完备，首跑联网失败] → 主 artifact 已实测在本地仓库；失败即按纪律停下报告镜像/网络问题，禁改回默认 settings；属环境风险非代码风险。
- [矩阵误报"方案 A 证伪"级失败（运行期找不到编译期 API）] → 该形态出现即阻断：停下汇报，不得给单侧模块换依赖树使其变绿（护栏语义大于便利）。
- [tools.jackson API 与 Jackson2 的镜像假设不成立（如日期默认形态差异）] → D-5 的断言镜像当场暴露；届时以断言为准修实现，不改测试迁就。
- [examples 双份复制矩阵源码漂移（将来只改一侧）] → diff 哨兵入出口核对；矩阵文件小，评审可见性高。
- [root verify 时长随 examples/矩阵增加上升] → examples 只加编译与 context 冒烟（秒级）；矩阵 IT 复用模块级单例容器；接受量级不变、轮次增加。
- [demo 真机冒烟手工执行，可能被跳过] → 出口标准硬约束：两 demo 各一轮留痕 + drift-WARN 观察记录，缺失即 M3 未出口。
- [Javadoc/引用门禁在新模块首触（示例源码从未受此约束）] → 写入各任务步骤而非依赖记忆；首跑 verify 即暴露，返工成本限于注释。

## Migration Plan

全增容型变更：新模块进 reactor、文档落盘，无数据/接口迁移。回退=从根 modules 移除对应模块 + 删除目录，既有产品模块不受牵连。实施顺序即任务顺序：T16 矩阵（方案 A 生死判最先兑现）→ T17 jackson3（其 opt-in IT 依赖矩阵存在）→ T18 examples（消费前两者稳定行为）→ T19 文档（转录前三者的已验证事实）。
