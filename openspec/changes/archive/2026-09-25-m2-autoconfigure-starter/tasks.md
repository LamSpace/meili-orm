# Tasks

> 里程碑实施计划（docs/superpowers/plans/2026-09-19-meili-orm-m0-m3.md）Task 12–15 在本 change 内展开；计划文本与 core 实况冲突处（taskUid 已全链路 `int`、无 `newBase` 工厂）以实况与设计 D-A/D-C/D-D 为准。所有 mvn 命令必须 `-s /home/lam/repo/settings.xml`。

## 1. 前置与模块基线

- [x] 1.1 确认 Docker 前置：`docker info` 守护进程可用，`docker images getmeili/meilisearch` 含 `v1.49.0`（缺失即停，属人工准备项，不得静默跳过后补）。验证：两条命令输出符合预期。
- [x] 1.2 autoconfigure pom 增加 `spring-boot-configuration-processor`（optional）与 `spring-boot-starter-test`（test 作用域），首跑联网拉取 3.5.16 构件（本地仓库现缺）。验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-spring-boot-autoconfigure -am clean verify` 绿，且 `/home/lam/repo/org/springframework/boot/spring-boot-configuration-processor/3.5.16` 存在；拉取失败即停报告，不改用默认 settings。

## 2. 客户端层自动配置（autoconfigure-client）

- [x] 2.1 先写红 L2 测试 `MeiliClientAutoConfigurationTest`：属性默认值/覆盖、`meili.enabled=false` 零 bean、非法枚举值绑定失败。验证：`-pl meili-orm-spring-boot-autoconfigure test` 编译失败或全红。
- [x] 2.2 实现 `MeiliProperties`、`MeiliConnectionDetails` + `PropertiesMeiliConnectionDetails`、`MeiliConfigCustomizer`、`MeiliClientAutoConfiguration`（Client 保持默认 GsonJsonHandler），写入 imports 第一行；全模块 Javadoc 齐备（含私有成员）并**移除 pom 中 javadoc `skip=true` 豁免**。验证：2.1 全绿。
- [x] 2.3 补齐条件链与哨兵测试：用户 Client bean back-off；customizer 有序多实例调用；用户 ConnectionDetails 覆盖属性实现；经捕获型 customizer 断言构建前 Config 的 hostUrl/apiKey 透传且 `jsonHandler` 为 `GsonJsonHandler`（设计 D-D 常驻哨兵）。验证：`-pl meili-orm-spring-boot-autoconfigure test` 全绿。
- [x] 2.4 `bash scripts/check-source-citations.sh` 零命中（src/main 注释不得引用 spike/计划/设计节号，措辞只读自洽）；提交 `feat(autoconfigure): 客户端层自动配置（属性/ConnectionDetails/Customizer/条件链+装配哨兵）`。

## 3. 数据层自动配置（autoconfigure-data）

- [x] 3.1 先写红测试 `MeiliDataAutoConfigurationTest`：Client 存在时 serializer/mappingContext/callbacks/operations 四类 bean 唯一产出、Client 缺失时不加载；容器 ObjectMapper 择优（SNAKE_CASE 探针经 `write()` 可观测）；无容器 mapper 时 Long 精度/ISO 日期/未知字段忽略；用户 serializer bean back-off；`MeiliCallback` bean 收集后 `registeredCount()>0`。验证：编译失败或全红。
- [x] 3.2 实现 `MeiliDataAutoConfiguration`（`ObjectProvider<ObjectMapper>.getIfAvailable` 模式，自建路径 `new ObjectMapper()` 裸 base，设计 D-A；`ObjectProvider<MeiliCallback>` 统一收集，设计 D-E）与 `MeiliEntityScanner`（AutoConfigurationPackages 缺包 → 空集合 + DEBUG，设计 D-F）；imports 追加第二行；Javadoc 同步。验证：3.1 全绿。
- [x] 3.3 模块级 `mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-spring-boot-autoconfigure clean verify` 绿；引用扫描零命中；提交 `feat(autoconfigure): 数据层自动配置与 @MeiliDocument 扫描器`。

## 4. 索引自动初始化（index-auto-initialization）

- [x] 4.1 先写红单测 `MeiliIndexInitializerTest`（Mockito 网关，**taskUid 一律 int**）：空实体集 no-op；`NONE` 零交互；`CREATE_IF_MISSING` 缺失→createIndex+推投影+awaitTask(int)、已存在→零写；`SYNC_SETTINGS` 无漂移 DEBUG 零写；diff 只比投影声明键、searchable 序列敏感其余集合语义；drift=warn 只告警、drift=apply 含 filterable/sortable 差异→推送+重建代价 WARN、drift=fail 抛 MeiliMappingException 且零写；重复 indexName fail-fast（消息含索引名与类名）；裸实体建索引但 `updateSettings` 零调用；网关抛 MeiliIndexAccessException → 原样传播（设计 D-C）。验证：编译失败或全红。
- [x] 4.2 实现 `MeiliIndexInitializer`（`SmartInitializingSingleton`，逻辑收在公有 `initialize()`，设计 D-B）+ `MeiliInitializationAutoConfiguration`（after 数据层，`@ConditionalOnMissingBean`）；imports 追加第三行；Javadoc 同步。验证：4.1 全绿。
- [x] 4.3 真机 IT `MeiliIndexInitializationIT`（autoconfigure pom 增 test 依赖 testcontainers/junit-jupiter + core test-jar）：`@EnableAutoConfiguration` 上下文 + `@DynamicPropertySource` 注入容器 url/key；断言启动后索引存在、settings 含投影数组；同实体加 filterable 字段以 SYNC+APPLY 二次启动 → settings 已更新且任务 SUCCEEDED；以不可达 url 启动 → 上下文刷新失败且异常为 MeiliIndexAccessException。验证：`-pl meili-orm-spring-boot-autoconfigure verify` 含 IT 全绿（需 Docker）。
- [x] 4.4 引用扫描零命中；提交 `feat(autoconfigure): IndexInitializer（三模式/diff/漂移策略/重建告警/启动期 fail-fast）+ 真机 IT`。

## 5. Starter 聚合与配置元数据（starter-packaging）

- [x] 5.1 先写红测试 `MeiliStarterMetadataTest`：imports 文件恰三行且逐行 `Class.forName` 成功；`target/classes/META-INF/spring-configuration-metadata.json` 含全部 `meili.*` 属性、`index.auto-init` 与 `index.on-settings-drift` 带枚举值 hints。验证：失败。
- [x] 5.2 补 `src/main/resources/META-INF/additional-spring-configuration-metadata.json`（hints）与 starter pom `<description>`/依赖核对（spring-boot-starter + core + autoconfigure，纯聚合无源码）。验证：5.1 转绿。
- [x] 5.3 提交前自查 `bash scripts/check-source-citations.sh --selftest` 通过 + 模块 verify 绿；提交 `feat(starter): 聚合 pom 与配置元数据`。

## 6. M2 出口（跨组装集成验证）

- [x] 6.1 全 reactor 回归：`mvn -s /home/lam/repo/settings.xml -q clean verify` 全绿——M0 spike 哨兵、M1 L1 全量与 MeiliCoreIT 真机链路原样通过（core 零改动的归因防线）。验证：BUILD SUCCESS。
- [x] 6.2 真机冒烟（Boot 4.0.3，用户已确认）：`/tmp/smoke` 临时最小应用仅引 `spring-boot-starter-meili-orm` 坐标 + 3 行配置（meili.url/api-key 指向本机 Docker v1.49.0 容器，auto-init 默认）→ `spring-boot:run` 启动成功、日志可见索引创建/settings 推送、`MeiliSearchOperations` bean 存在可注入。验证：命令输出与日志关键行贴进出口 commit message；结束清理临时容器。
- [x] 6.3 `openspec validate m2-autoconfigure-starter --strict` 通过；提交 `test+docs: M2 出口冒烟通过（Boot 4.0.3 真机）` 或并入末次提交。出口核对：设计文档 §8 M2 出口"加依赖 + 3 行配置即用"打勾。
