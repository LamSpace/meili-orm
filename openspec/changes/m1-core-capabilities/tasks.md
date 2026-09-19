# Tasks: meili-orm M1 · meili-orm-core

> 执行基线：实施计划 `docs/superpowers/plans/2026-09-19-meili-orm-m0-m3.md` Task 4–11 的 TDD 步骤与代码骨架**全部沿用**，本清单只记录**修正与裁决覆盖项**（冲突处以本清单为准）；每任务红→绿→commit（conventional，中文描述），全部 mvn 命令带 `-s /home/lam/repo/settings.xml`。契约以本 change specs/ 为准，实现形态以 design.md D-M1-1…8 为准。

## 1. 注解集 + 元模型 + 异常体系（计划 Task 4）

- [x] 1.1 按计划 Step 1–3 落地 `mapping/` 注解五件 + `MeiliNames/MeiliPersistentProperty/MeiliPersistentEntity/MeiliMappingContext` 与 `exception/` 四件；覆盖 spec `core-entity-mapping` 全部 Requirement（record 形态、点路径防环、@JsonProperty 冲突、缓存同实例）
- [x] 1.2 【修正】本任务为 core 首个主源码：删除 `meili-orm-core/pom.xml` 的 maven-javadoc-plugin `<skip>`，全部新类（含私有成员）按 CLAUDE.md 契约分量级补齐 Javadoc；`bash scripts/check-source-citations.sh` 对新 src/main 零命中（DoD 并入 1.3 验证）
- [x] 1.3 验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-core verify`（javadoc 引擎实跑通过）；commit `feat(core): 映射注解集与实体元模型（嵌套点路径、主键校验、冲突 fail-fast）`

## 2. 序列化器（计划 Task 5）

- [x] 2.1 `MeiliDocumentSerializer`（仅 write/read 两方法，spec `core-document-serialization` SPI Requirement）+ `Jackson2DocumentSerializer`（introspector 取名与 `MeiliNames.docName` 同规则；base.copy() 不污染原 mapper）；红→绿按计划 Step 1–4；commit `feat(core): MeiliDocumentSerializer 接口与 Jackson2 实现（@MeiliField.name 序列化一致性）`

## 3. 查询 IR + 强类型结果（计划 Task 6）

- [x] 3.1 `MeiliQuery`/`DocumentsFetchQuery`/`MatchingStrategy`/`MeiliSearchResult` + `internal/SdkQueryTranslator`；【前置回改】builder 含 `filterAdd(String)`（AND 累积）；【修正】分页互斥/raw 白名单/双 filter 混设三类拒绝均抛 `MeiliOrmException` 且消息合规；SDK 覆盖面已 javap 实证（vector/hybrid/distinct/locales 全有 setter），`raw()` 白名单 = SearchRequest 既有 setter 键名表
- [x] 3.2 验证：`-Dtest=SdkQueryTranslatorTest,MeiliSearchResultTest` 绿 + 全模块 test 绿；commit `feat(core): 查询 IR、SDK 翻译与强类型搜索结果（Long 无损信封解析）`

## 4. Settings 投影（计划 Task 7）

- [x] 4.1 `ProjectedSettings`/`MeiliSettingsProjection`（纯函数、透传白名单与深合并、未知键 fail-fast 含文件名）；golden 三件（books-settings/passthrough/unknown-key）按 D-M1-7 固定键序逐字节锁定；spec `core-settings-projection` 四 Requirement 全覆盖；commit `feat(core): Settings 投影（角色数组、透传合并、golden 文件锁定）`

## 5. 回调四件套（计划 Task 8）

- [x] 5.1 `event/` 四接口 + `MeiliEntityCallbacks`；【前置回改】`registeredCount()` 纳入本任务契约（spec `core-entity-callbacks` 可观测 Requirement）；泛型不可解析拒绝注册、注册序链式、父类匹配；Javadoc 声明线程模型（注册期/触发期）；commit `feat(core): 四件套实体回调与泛型注册表`

## 6. 网关 + 写读删 Operations（计划 Task 9）

- [x] 6.1 计划 Step 1 的 SDK 探测**已完成**，直接采用结论：`count` 无 SDK 方法 → 走网关 HTTP helper `GET /indexes/{uid}/documents/count`；raw 方法名 = `getRawDocument(String)`；`waitForTask(int,int,int)`；连接参数取 `client.getConfig().getHostUrl()/getApiKey()`（D-M1-2）
- [x] 6.2 【裁决覆盖】`SdkMeiliRawGateway` 双通道：文档读写/搜索/任务/索引增删走 SDK raw API；count、`POST /documents/fetch`（含 sort）、`GET/PATCH /indexes/{uid}/settings` 走包私有 OkHttp HTTP helper；`getSettings` 返回服务端真实 JSON 原文（spec `core-operations` 网关 Requirement），settings 不再经 typed `Settings` 重序列化；两通道异常经同一 `MeiliErrors.translate`
- [x] 6.3 【修正】taskUid 全链路 `int`：网关签名 `int updateDocuments(...)`、`awaitTask(int, Duration)`、`MeiliTask.getUid(): int`；`MeiliSearchOperations` 写读段按计划签名但 uid 类型替换；Mockito 网关测试 + 错误翻译测试按计划 Step 2 落地（含"空主键不触网/批量单请求/wait-task 阻塞"断言）；commit `feat(core): SdkMeiliRawGateway 双通道与写读删 Operations（task 全 int、异常统一出口）`

## 7. 搜索/索引 Operations（计划 Task 10）

- [ ] 7.1 追加 search/multiSearch/indexExists/createIndex/deleteIndex/applySettings/projectedSettings；`createIndex/applySettings` 返回 `int`；multiSearch 串行 + 非原子 Javadoc（D-M1-5）；createIndex 主键名取元模型、投影非空才推 settings（含"无角色 never updateSettings"断言）；commit `feat(core): 搜索/索引 Operations 与 settings 推送`

## 8. M1 出口（计划 Task 11 + 收口）

- [ ] 8.1 `MeiliCoreIT` 真机全链路（计划 Step 1 场景 + 新增两条：`findAll` filter+sort 真机反查（v1.49 fetch 语义验证，不符则按 design 风险条目收窄并回写 spec）、404→`MeiliIndexAccessException.getMeiliCode()` 与空 Optional 各一次）
- [ ] 8.2 `PublicApiLeakageGuardTest`：反射扫描 core 非 internal 包公开成员签名，出现 `com.meilisearch.sdk.*` 即红（D-M1-3）；故意加一个泄漏签名验证守卫能红、再删除
- [ ] 8.3 全量验证：`mvn -s /home/lam/repo/settings.xml -q clean verify`（单测 + 双哨兵 IT + MeiliCoreIT + javadoc + 守卫全绿）；`bash scripts/check-source-citations.sh` 零命中
- [ ] 8.4 出口审查材料：生成 core 公开 API 签名清单（javadoc 输出或反射 dump）供用户目检一次
- [ ] 8.5 回写：设计文档 §5.3 签名（int taskUid、createIndex 返回 int、count 直连事实）；实施计划文档 M1（Task 4–11）复选框收口；commit `test(core): M1 真机全链路 IT、零泄漏守卫与出口回写`
- [ ] 8.6 `openspec validate m1-core-capabilities` 通过后归档 change（delta specs 同步主 specs，沿用 M0 归档流程）
