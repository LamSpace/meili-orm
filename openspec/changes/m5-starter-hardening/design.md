# Design

## Context

动机见 proposal.md - Why。三条塑造方案的事实约束（均已实证）：

1. SDK 0.21.0 `Config` 源码：`clientAgents` 仅能经 `Config(String hostUrl, String apiKey, String[] clientAgents)` 构造器传入；私有 `configHeaders` 将用户列表 `add(0, Version.getQualifiedVersion())` 后以 `;` join 写入 `User-Agent` 头，`headers` 字段 `protected final`，无 setter。即组装语义是**前置追加**，SDK 版本 token（`MeiliSearch Java SDK (v0.21.0)`）免费自带。
2. `Version.getQualifiedVersion()` 为 public static——L2 测试可动态取期望前缀，不把 SDK 版本串硬编码进测试。
3. `.mvn/maven.config`（本机 `--settings` 兜底）已在 `.gitignore` 中、不被 git 跟踪，CI 检出物中天然不存在，无需处理冲突。

## Goals / Non-Goals

**Goals:**
- `meili.client-agents` 从属性到 User-Agent 头的完整链路，含默认值、空值回退、多条目顺序三个边界。
- 一条与本机纪律同构（显式 settings、全量 verify、双门禁）且零本机路径依赖的 CI 通道。
- 设计文档三处状态修正；对外可提交的上游 issue 草稿。

**Non-Goals:**
- 不在 client-agents 里携带 starter 自身版本号（见 D2）。
- 不在本变更中接入 Testcontainers `@ServiceConnection`（另立 change）。
- 不改 SDK 钉版、不改 core/repository/jackson3 任何公开 API。
- CI 不做发布（deploy/release）流水线，仅验证。

## Decisions

### D1 client-agents 接线：构造器传参，属性 → `String[]`
`MeiliProperties` 增 `List<String> clientAgents`，`meiliClient` bean 方法改调 `new Config(url, apiKey, clientAgents.toArray(String[]::new))`。
**否决备选**：(a) 用 `MeiliConfigCustomizer` 改 headers map——customizer 是给用户的扩展点，starter 自用是角色错位，且 `headers` final、map 内容在 bean 方法外改动语义不清；(b) 按设计文档字面把完整 UA 串 `meilisearch-java:0.21.0; meili-orm` 整串传入——会与 SDK 前置的自身 token 撞车，产生双重 SDK 串。

### D2 默认值：单 token `meili-orm`，不带版本
属性默认 `["meili-orm"]`；显式配空值 → `new String[0]` → UA 仅 SDK 默认 token。版本化 token（`meili-orm/1.0.0`）需运行时读 jar manifest，在非 fat-jar/shaded 场景易失真，收益不值当；发布元数据就位后另议。空字符串绑定到空列表是 Spring `StringToCollectionConverter` 的既有特例，L2 测试钉死；若实测绑定异常，退化为要求 `[]` 写法并在 metadata description 标注。

### D3 观测点：L2 用 customizer 捕获构建前 Config 断言 headers
`configHeaders` 在构造期完成，customizer 拿到的实例已含最终 `User-Agent`。三条断言：默认值 = `Version.getQualifiedVersion() + ";meili-orm"`；空值 = 恰好 SDK token；多条目保序。不引入真机断言——服务端不回显 UA，L2 即终点。

### D4 CI 形态：GitHub Actions 单 job，全量 reactor verify
`ubuntu-latest`（自带 Docker）+ `setup-java`（temurin，JDK 取构建基线一致的最高 LTS 可用版，`cache: maven`）；步骤 = checkout → setup-java → `mvn -s ci/settings.xml -B clean verify` → `bash scripts/check-source-citations.sh --selftest` && `bash scripts/check-source-citations.sh`。触发：push（master）+ pull_request；`timeout-minutes: 60`。Testcontainers 首跑在线拉 `getmeili/meilisearch:v1.49.0`，与本机镜像版本一致。
**否决备选**：拆分多 job 矩阵（boot3/boot4 各自独立 job）——首轮不做，全 reactor 单 job 先立护栏，时长超预算再拆（拆法不改验证契约）。

### D5 `ci/settings.xml`：提交仓库的无凭据最小 settings
仅声明默认本地仓库、无镜像/凭据覆盖。目的不是功能必需，而是把"mvn 必须显式 `-s`"的纪律同构延续到 CI，杜绝"CI 忘带 settings 也能绿、本机换环境就红"的漂移。GitHub-hosted runner 直连 Maven Central，无需镜像。

### D6 设计文档修正与 issue 草稿：纯转录，不新增语义
设计文档改三处：头部状态行（"待终审"→ 定稿 + 日期）、§9 D2–D4 行补拍板结论、§2.4 现状行刷新为当前七模块。上游 issue 草稿 `docs/upstream-okhttp-injection.md` 用英文撰写（对外发布标准），内容限既有实证（0.21.0 构造面、spikeA/冒烟记录、超时不可配限制），**提交动作归维护者本人**，任务只到"草稿落盘"。

## Risks / Trade-offs

- [CI 全量 verify（双矩阵 + 全部 Testcontainers IT）时长可能超预期] → 首轮接受，Maven 缓存热后复测；超预算按 D4 备选拆 job，验证契约不变。
- [User-Agent 期望前缀随 SDK 升级漂移] → L2 断言动态取 `Version.getQualifiedVersion()`，SDK 自身格式变化不构成契约破坏；钉版升级本就伴随 spike 哨兵复跑。
- [`meili.client-agents=` 空串绑定行为若不符预期] → D2 已留退化路径（`[]` 写法 + metadata 标注），spec 只承诺"显式空值回退"语义，两种实现均满足。
- [runner JDK 若暂无 25] → release=17 字节码基线与构建 JDK 解耦，降级 JDK 构建不影响产物契约；CI 记录实际 JDK 版本即可。
- [门禁脚本依赖 bash 环境] → ubuntu runner 原生满足。

## Migration Plan

纯增量：属性可选、CI 旁路、文档修订。回滚 = revert 提交，无数据/配置迁移。应用方零感知，除非其显式设置了 `meili.client-agents`。
