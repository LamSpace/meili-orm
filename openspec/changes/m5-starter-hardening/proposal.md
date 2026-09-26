# Proposal

## Why

设计文档配置属性表承诺了 `meili.client-agents`（SDK User-Agent 扩展），但实施计划从 M2 任务接口起即漏抄该属性，实现、配置元数据与全部既有 specs 中均无踪影——这是属性表与代码之间唯一的真实缺口，且 `MeiliConfigCustomizer` 无法补救（SDK `Config` 的 `headers` 为构造期生成，javap 实证 clientAgents 仅能经 `Config(String,String,String[])` 构造器传入）。同时，CLAUDE.md 门禁声明"提交前自查与 CI 共用"，仓库却无任何 CI workflow：双代矩阵、Testcontainers IT、引用/ Javadoc 门禁目前只靠本机手工执行；设计文档头部仍挂"待终审"、§9 决策表 D2–D4 未记录已拍板结论。首版发布前需一并收口。

## What Changes

- 新增配置属性 `meili.client-agents`（`List<String>`，默认单元素 `meili-orm`）：`MeiliClientAutoConfiguration` 改走 `Config(String,String,String[])` 构造器注入。User-Agent 组装语义按 SDK 0.21.0 `configHeaders` 源码实证为"SDK 自身版本 token 自动前置 + 用户列表以 `;` 追加"，starter 不再（也不应）伪造 SDK 版本串；显式配置为空列表则回退纯 SDK 默认 UA。纯新增属性，无 **BREAKING**。
- 新增 CI 验证通道：`.github/workflows/verify.yml` + 提交一份无凭据的 `ci/settings.xml`（显式 `-s` 纪律延续到 CI；本机 `.mvn/maven.config` 本就不在版本控制内，互不影响）。workflow 在带 Docker 的 ubuntu runner 上跑全 reactor `clean verify`（含双代矩阵与 Testcontainers IT，服务端钉 v1.49.0）并执行 `check-source-citations.sh --selftest` 与门禁脚本本体。
- 设计文档状态修正：头部"状态：待终审"改为定稿并记日期；§9 待终审决策表补记 D2（坐标命名定稿）、D3（v1 排除项确认）、D4（文档语言确认）的拍板结论；§2.4"现状"行由 M0 三模块描述刷新为当前七模块结构。
- 上游 SDK feature request 草稿：产出 `docs/upstream-okhttp-injection.md`（向 meilisearch-java 提 OkHttpClient 注入点的 issue 正文，含 0.21.0 构造面实证与 spike 记录引用），提交动作由维护者本人执行，不在代码范围内。

## Capabilities

### New Capabilities

（无——本变更全部落在既有能力面上。）

### Modified Capabilities

- `autoconfigure-client`：`meili.* 配置属性集` requirement 的属性清单加入 `client-agents`；新增 requirement 约束 client-agents 到 SDK `Config` 构造器的接线与 User-Agent 组装语义（含空列表回退、customizer 仍末位生效）。
- `module-build-foundation`：新增"CI 持续集成验证"requirement——仓库 SHALL 提供在带 Docker 的执行器上运行全 reactor 构建门禁与双代矩阵的持续集成流水线，且 SHALL NOT 依赖任何本机专属路径/凭据。

设计文档状态修正与上游 issue 草稿为纯文档产出，不引入 spec 级行为，不单独立能力。

## Impact

- **代码**：`meili-orm-spring-boot-autoconfigure`（`MeiliProperties`、`MeiliClientAutoConfiguration`、`additional-spring-configuration-metadata.json`）；core / repository / jackson3 / starter 零改动。
- **测试**：autoconfigure L2 新增 client-agents 断言（经 customizer 捕获构建前 Config 观测 `headers` 的 User-Agent 串）；it-boot3/it-boot4 无需新用例（装配路径共用）。
- **仓库基础设施**：新增 `.github/workflows/verify.yml`、`ci/settings.xml`；`.gitignore` 不变。
- **文档**：根设计文档、`docs/upstream-okhttp-injection.md`（新增）、README 限制清单/属性表若涉及则同步。
- **构建纪律**：Javadoc 门禁（show=private）对新增属性访问器自动生效；引用门禁对新增注释生效。
- 不新增第三方依赖，不改版本钉，不涉及发布元数据变更。
