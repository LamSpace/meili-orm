# Tasks

## 1. meili.client-agents 属性与接线

- [x] 1.1 在 `MeiliClientAutoConfigurationTest` 新增三条 L2 失败用例（默认值 = SDK token 前缀 + `;meili-orm`、显式空值 = 恰为 SDK token 且无分隔符残留、`a,b` 多条目保序）；期望前缀动态取 `com.meilisearch.sdk.Version.getQualifiedVersion()`，经 `MeiliConfigCustomizer` 捕获构建前 Config 的 `headers` 观测。验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-spring-boot-autoconfigure test` 因新用例失败（红）。
- [x] 1.2 `MeiliProperties` 增 `List<String> clientAgents`（默认 `["meili-orm"]`，Javadoc 齐备），`meiliClient` bean 方法改走 `new Config(url, apiKey, String[] clientAgents)` 构造器（保持默认 GsonJsonHandler 不变，customizer 仍末位逐个作用）。验证：1.1 用例转绿；空值绑定若与预期不符，按设计 D2 退化路径处理（`[]` 写法 + metadata description 标注）并在用例中钉死实际行为。
- [x] 1.3 `additional-spring-configuration-metadata.json` 增补 `meili.client-agents` 的 description（说明追加语义与空值回退）。验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-spring-boot-autoconfigure test` 全绿，且 `target/classes/META-INF/spring-configuration-metadata.json` 中 `meili.client-agents` 在册（processor 生成核对）。
- [x] 1.4 README 配置属性表加入 `meili.client-agents` 一行（默认值 `meili-orm`）。验证：属性表与 `MeiliProperties` 字段逐项对照一致，README 其余内容零改动。

## 2. CI 验证流水线

- [x] 2.1 提交 `ci/settings.xml`：无凭据、无镜像覆盖的最小显式 settings。验证：本机执行 `mvn -s ci/settings.xml -q validate` 成功（离线模式 `--offline -s ci/settings.xml validate` 亦通过，证无远程仓库改写）。
- [x] 2.2 新建 `.github/workflows/verify.yml`：push(master)+pull_request 触发、`ubuntu-latest`、`setup-java`（最高可用 LTS JDK + `cache: maven`）、步骤 `mvn -s ci/settings.xml -B clean verify` → `bash scripts/check-source-citations.sh --selftest` → `bash scripts/check-source-citations.sh`，`timeout-minutes: 60`。验证：workflow 文件内不出现任何 `/home/lam` 类绝对路径与凭据（grep 核对），YAML 语法本地解析通过。
- [x] 2.3 README 构建说明处补一句 CI 事实（push/PR 触发全量验证）。验证：README 相关段落读后自洽，与实际 workflow 文件名一致。
- [ ] 2.4 推送后观察首轮 Actions 运行结论；若 Testcontainers 拉镜像或耗时超预算，按设计 D4 备选拆 job 并记录。验证：CI 上一轮全绿运行记录（链接/截图写入提交说明或 PR 描述）。

## 3. 文档状态修正与上游草稿

- [x] 3.1 设计文档 `2026-09-19-meili-orm-starter-design.md` 三处修正：头部"状态：待终审"改为"已定稿（2026-09-26）"；§9 D2/D3/D4 行补拍板结论（D2 定稿、D3 确认、D4 是）；§2.4 现状行刷新为当前模块结构（core/autoconfigure/starter/jackson3/repository/it×2/examples×3）。验证：全文 grep 无"待终审"残留；现状行与根 pom `<modules>` 一致。
- [x] 3.2 新建 `docs/upstream-okhttp-injection.md`：英文 issue 草稿（标题、0.21.0 构造面实证、OkHttpClient/Builder 注入点诉求、超时不可配影响、可提供的最小复现），仅引用公开事实与 docs/spikes.md 既有结论。验证：文件存在、通读可直接粘贴提交；不含内部过程材料与代号（人工对照 + `bash scripts/check-source-citations.sh` 零退出兜底）。

## 4. 集成验证

- [x] 4.1 全 reactor 出口核对：`mvn -s /home/lam/repo/settings.xml -q clean verify` 全绿（含双矩阵与全部 IT）、`bash scripts/check-source-citations.sh --selftest` 与脚本本体零退出、`openspec validate m5-starter-hardening --type change` 通过。验证：三条命令输出原文留痕于提交说明。
