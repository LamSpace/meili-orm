# Tasks

## 1. 双代兼容矩阵（it-boot4 / it-boot3）

- [x] 1.1 建 `it/pom.xml` 聚合 + `it/meili-orm-it-boot4`、`it/meili-orm-it-boot3`、`it/meili-orm-it-boot4-jackson3` 模块 pom（前两者各自 dependencyManagement 首位 import 对应代 `spring-boot-dependencies`：4.0.3/3.5.16，显式写出以对称自证；依赖 starter、core test-jar、spring-boot-starter-test、testcontainers junit-jupiter；第三模块 dm 镜像 boot4 侧并依赖基线 it-boot4 的 test-jar + core test-jar，jackson3 依赖留待 2.5；三模块均配 failsafe、`maven.deploy.skip=true`），根 pom `<modules>` 追加 `it`；验证：`mvn -s /home/lam/repo/settings.xml -q -pl it -am validate` 成功且 `dependency:tree -pl it/meili-orm-it-boot3`/`-pl it/meili-orm-it-boot4` 中 Boot 坐标分别全为 3.5.16/4.0.3
- [x] 1.2 it-boot4 写 `MeiliStarterIT` + `ITBook` + `ItApp`（真机：`MeiliContainer` url/key 注入属性 `wait-task=true`、`auto-init=sync-settings`、`on-settings-drift=apply`；断言版本哨兵 startsWith "4." 且 `.as("矩阵版本钉定漂移")`、注入 Operations/serializer 为 Jackson2 实现、save(Long 9007199254740993)→findById→search 往返无损，启动 initializer 建索引后 indexExists 为真）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl it/meili-orm-it-boot4 -am verify` 绿（Docker 前置）。任何"运行期找不到编译期 API"形态失败=方案 A 证伪信号：停下汇报，不得改依赖树救绿
- [x] 1.3 复制 IT 到 it-boot3（哨兵期望改 "3."，其余逐字节一致，`diff` 核对仅哨兵行差异）并首跑（boot3 传递树若有缺失依赖联网拉取，失败=环境问题停下报告，禁改默认 settings）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl it/meili-orm-it-boot3 -am verify` 绿。组尾 commit（conventional，中文描述）

## 2. 可选模块 meili-orm-serializer-jackson3

- [x] 2.1 模块 pom（自身 dependencyManagement 首位 import Boot 4.0.3 BOM 以解析 `tools.jackson`；依赖 core、spring-boot-autoconfigure、tools.jackson jackson-databind；`maven.compiler.release=17`；package-info 齐备）+ 根 modules 追加；验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-serializer-jackson3 -am package` 绿且 `dependency:tree` 确认 tools.jackson 仅本模块在册（core/autoconfigure/starter/it-boot3 均无）
- [x] 2.2 先写 `Jackson3DocumentSerializerTest`（镜像 Jackson2 断言集四条：`book_title` 改名、Long 逐位无损、ISO 日期非时间戳、未知键忽略）确认红，再实现 `Jackson3DocumentSerializer`（tools.jackson mapper copy + `@MeiliField.name` introspector 镜像 Task 5 规则，注解取路径覆盖字段/组件/getter）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-serializer-jackson3 test` 该测试绿
- [x] 2.3 实现 `MeiliJackson3SerializerAutoConfiguration`（`@AutoConfiguration(before = MeiliDataAutoConfiguration)`、`@ConditionalOnClass(tools.jackson ObjectMapper)`、`@ConditionalOnMissingBean(MeiliDocumentSerializer)`，容器有 Jackson3 mapper bean 则以其为底否则自建）+ 模块自有 imports + L2 测试（takesOver/userBeanWins/用户 mapper 命名策略被尊重）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-serializer-jackson3 test` 全绿
- [x] 2.4 it-boot4 补 jar-plugin test-jar execution（使 `ITBook`/`ItApp` 可复用；重跑本模块 verify 确认仍绿）
- [x] 2.5 `it-boot4-jackson3` opt-in IT：加 jackson3 模块 test 依赖 + 基线 it-boot4 test-jar + core test-jar；`Jackson3WiringIT` 复用 `ItApp`/`ITBook` 上下文（同 @DynamicPropertySource 模式），断言 serializer instanceof Jackson3DocumentSerializer 且 save→findById→search 真机往返无损；it-boot3 与基线 it-boot4 零改动（"类缺席"分支由二者证明）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl it/meili-orm-it-boot4-jackson3 -am verify` 绿 + 根 reactor `clean verify` 绿。组尾 commit

## 3. examples 双代 demo

- [x] 3.1 `examples/pom.xml` 聚合 + common/boot3/boot4 三模块 pom（common 编译基线 3.5.16，依赖 starter + spring-boot-starter-web；两 app 首位 import 各代 BOM、`spring-boot-maven-plugin`、`maven.deploy.skip=true`）+ 根 modules 追加；验证：`mvn -s /home/lam/repo/settings.xml -q -pl examples -am package` 绿（暂无业务源码）
- [x] 3.2 common 业务源码：`Book`（`Long id` 主键、`book_title` searchable order=1、`overview` searchable、`Author{name,city}` city filterable、`tags`/`genre` filterable、`price` sortable+filterable、`publishedAt` sortable、`@JsonIgnore` 排除字段）、`ExampleConfig`（`BeforeConvertCallback<Book>` 规整 title + `@MeiliSetting` 透传 `classpath:meili/books-settings.json`：rankingRules + stopwords"的"）、`BookController`（import/search/{id}/delete/raw 端点按 spec §7；`data.json` 用显式 `new` Jackson2 ObjectMapper 读取，不注入容器 mapper；filter 累积用 `filterAdd`）、`meili/books-settings.json`、`data.json`（6 本中文书，含 id=9007199254740993）；src/main 全部成员含私有按 CLAUDE.md §5 写足 Javadoc；验证：`mvn -s /home/lam/repo/settings.xml -q -pl examples/meili-orm-example-common -am package` 绿 + `bash scripts/check-source-citations.sh` 绿
- [x] 3.3 两 app：启动类（`@SpringBootApplication(scanBasePackages=…example)`）+ `application.yml`（`meili.url/api-key=demoMasterKey-0123456789`、`wait-task=true`、`auto-init=sync-settings`、`on-settings-drift=warn`）+ 各一个零 Docker context-load 冒烟测试（`@SpringBootTest` + 属性覆盖 `auto-init=none` + 未监听端口 url，断言控制器/回调/serializer/operations bean 齐备）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl examples/meili-orm-example-boot4 -am verify` 与 `-pl examples/meili-orm-example-boot3 -am verify` 均绿且测试全程无 Docker。组尾 commit
- [x] 3.4 boot4 真机冒烟：`docker run -d --name meili-demo -p 7700:7700 -e MEILI_MASTER_KEY=demoMasterKey-0123456789 -e MEILI_ENV=development getmeili/meilisearch:v1.49.0` → `mvn -s /home/lam/repo/settings.xml -q -pl examples/meili-orm-example-boot4 spring-boot:run` → 计划 §7 curl 集全执行（import 返回 6、中文 search 带 genre/minPrice/sort/page/facet 命中、`/9007199254740993` id 逐位无损、raw 出原始 JSON、delete 后查询空）；启动日志确认"索引创建+settings 推送"两行 INFO；drift-WARN 观察：临时给 Book 加一个 filterable 字段重启→日志出现漂移 WARN 且不 apply→`git checkout .` 还原；全部命令与实际输出转录进 `examples/README.md` 冒烟小节；验证：留痕小节每条输出与终端实际输出一致
- [x] 3.5 boot3 真机冒烟：同 3.4 流程 `-pl examples/meili-orm-example-boot3` 重跑一遍并留 boot3 版输出记录；完成后 `docker rm -f meili-demo` 清理。验证：boot3/boot4 各一份完整冒烟留痕在册。组尾 commit

## 4. 文档四件套（只转录已验证事实）

- [x] 4.1 根 `README.md`：starter 坐标 + yml 三行最小装配 + Book 实体与 search 代码样例 + 功能表（设计文档 §1.1 转录）+ demo 一键命令（3.4 实际记录转录）+ 构建命令含 `-s /home/lam/repo/settings.xml` 与 Docker/v1.49.0 前置声明 + 限制摘要清单 + 三份指南链接；验证：逐条命令可指认到任务 1–3 的实际执行记录，无一条臆造
- [x] 4.2 `docs/mapping-guide.md`：注解→settings 对照表（每注解一行）、"不标注=不声明"铁律含 displayed 白名单后果、嵌套点路径规则、`@MeiliSetting` 透传与投影合并/覆盖优先级、`searchableOrder` 排序语义、回调四件套样例、投影管线图；验证：按对照表构造实体的投影输出与 core golden 测试（`golden/books-settings.json`）一致，样例代码可编译
- [x] 4.3 `docs/limitations.md`：超时不可配（SDK 硬约束+exclusion/上游 issue 现状）、count 走 stats 语义、multiSearch 串行、Boot4 无容器 Jackson2 mapper 时自建行为、写后可查须 `wait-task`、改 filterable/sortable 全量重建代价、非目标表（响应式/@Version/analyzer/SpEL 索引名）——每条含 workaround；验证：逐项对照设计文档 §3.4/§1.2/§5.2 核销无漏项，且每条有代码事实或测试依据
- [x] 4.4 `docs/boot3-to-boot4.md`：编译基线策略（autoconfigure/examples-common=3.5.16、jackson3 为唯一 Boot 4.0.3 BOM 模块的例外及理由）、双矩阵工作方式与哨兵语义、jackson3 模块用法（Boot4 场景）、依赖升级检查清单（SDK/Boot/服务端镜像三类钉版变更→根 reactor 全绿含双矩阵、重读 spikeA/spikeB 哨兵结论、demo 真机冒烟一轮，各步给具体命令）；验证：清单中命令逐条可执行或 dry-run 通过（如 dependency:tree 类）。组尾 commit

## 5. M3 出口核对（跨组集成检查）

- [x] 5.1 全 reactor `mvn -s /home/lam/repo/settings.xml -q clean verify` 绿（core/autoconfigure/starter/jackson3/双矩阵/examples 全部模块）+ `bash scripts/check-source-citations.sh --selftest` 与无参运行均绿；验证：BUILD SUCCESS 输出留档
- [x] 5.2 对照设计文档 §8 M3 出口标准与本 change 5 份 spec delta 逐项打勾（两 demo 真机留痕、矩阵双绿含 Boot3.5.16 首拉验证、jackson3 接管与 Boot3 无感、文档评审过），结论以出口核对 commit 记录（含 `git log --oneline` 与本任务组号对照）；验证：commit 存在且 `git status` 干净、无未跟踪残留
