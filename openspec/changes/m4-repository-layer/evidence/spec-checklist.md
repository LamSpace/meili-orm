# M4 出口核对表（spec Requirement → 证据）

> 判定方式：每条 Requirement 对照其 Scenario 的实现测试/构建证据；命令为
> `mvn -s /home/lam/repo/settings.xml clean verify` 全 reactor 输出（7.1）与各测试类。

## repository-layer

| Requirement | Scenario | 证据 |
|---|---|---|
| MeiliRepository 接口与 CRUD 落点 | 仓库 CRUD 真机往返 | `MeiliRepositoryIT.derivedQueriesAgainstRealServer`（save/find/count/delete 段）+ `MeiliRepositoryMatrixIT.derivedQueriesRoundTripIdentically`（双代） |
| 同上 | 批量删除逐条生效 | `SimpleMeiliRepositoryTest.deleteAllIterableIssuesOneRequestPerEntity/deleteAllByIdLoopsSingleDeletes`（L1）+ IT 保存/删除链路 |
| 无界读取与分页总数语义 | findAll 截断可观测 | `SimpleMeiliRepositoryTest.findAllAtCeilingWarnsAboutTruncation`（logback 断言 WARN）；`missingTotalsFallBackToHitsSize` + `pageImplClampsTotalToCoverCurrentPage` |
| 同上 | 分页总数为估算值 | 同上 + `pageableBrowseTranslatesToPageModeWithBridgedSort` |
| 工厂装配与元数据桥接 | 主键类型解析一致 | `MeiliRepositoryProxyTest.entityInformationBridgesCoreMetamodel`；工厂自持形态见 `MeiliRepositoryProxy`/`MeiliRepositoryFactoryBean`（不继承漂移基类，spike 记录） |
| 启用路径与自动配置自注册 | 加依赖零配置即用 | `MeiliRepositoriesAutoConfigurationTest.fallbackScansAutoConfigurationPackages` |
| 同上 | 开关与自定义工厂退避 | `disabledSwitchRemovesRepositoriesButKeepsDataLayer` + `dataLayerAbsentSkipsSilently` + 用户工厂 back-off（条件链 `@ConditionalOnMissingBean` marker） |
| 同上 | 未引入模块的应用零扰动 | `MeiliStarterIT`（双矩阵未加 repository 依赖侧照常全绿）+ `enforcer-1.3.txt`（starter 依赖树无 commons） |
| 委托链路透明语义 | 回调经仓库生效 | `MeiliRepositoryLifecycleTest`（BeforeConvert/AfterConvert/wait-task 三断言） |
| （spike 修正） | 注解短路/角色报错类型 | 实现按 `MeiliRepositoryConfigurationException extends IllegalArgumentException` + 角色失败 `MeiliMappingException`，与 spec 一致 |

## repository-derived-queries

| Requirement | Scenario | 证据 |
|---|---|---|
| 关键字到 MeiliQuery 映射 | 等值与区间派生真机命中 | `MeiliRepositoryIT`（findByGenre/findByPriceGreaterThan/Between 段）；L1 黄金串全表 `MeiliDerivedQueriesTest` |
| 同上 | Containing 走全文通道 | `containingBecomesFullTextWithSearchOnScope`（q+attributesToSearchOn=book_title）+ 真机 `findByTitleContaining("三体")` |
| 同上 | In 空集合短路 | `inRendersListAndEmptyShortCircuits`（times(1) 断言无第二次请求） |
| 投影名桥接 | 改名属性派生正确投影 | `equalityRendersQuotedLiteral`（book_title）+ `nestedChainResolvesToDotPath` |
| 同上 | 未知属性启动即炸 | `abbreviationRejectedWithExplicitMessage` / `excludedAndAggregateAndUnknownPropertiesRejected` |
| 启动期角色预检 | 缺 filterable 启动失败 | `RolePrecheck.missingFilterable`（消息含 filterable/@MeiliSetting 双指引） |
| 同上 | 角色齐备通过预检 | 全部正向方法经 `MeiliRepositoryProxy.create` 构建成功 |
| 分页、排序与返回形态 | Pageable 换算正确 | `pageableConvertsToPageModeAndAppendsSort`（page=3/hitsPerPage=20/sort 桥接） |
| 不支持面启动期报错契约 | 不支持关键字定位报错 | `Unsupported.keywordSurface/distinctAndCountVerbsRejected/badReturnShapesRejected/arityMismatchRejected` |

## repository-query-annotation

| Requirement | Scenario | 证据 |
|---|---|---|
| 注解属性面与查询合成 | q 与 filter 组合 | `MeiliAnnotatedQueriesTest.qAndFilterCombine` + 真机（矩阵 IT `expensive`） |
| 同上 | 空注解启动失败 | `bootstrapValidation`（EmptyOnly → "至少提供"） |
| 参数绑定与字面量渲染 | 引号注入无害 | `injectionEscaped`（`科幻" OR price > 0 --` 整体成字面量）；双轨绑定 `positionalAndNamedMixed`；SpEL `spelExpressionEvaluates`/`spelFailureWrapped` |
| 同上 | 占位符缺失启动报错 | `bootstrapValidation`（:missing / ?9 越界） |
| 启动期模板合法性校验 | 括号不配对启动失败 | `bootstrapValidation`（BadParen → "括号不配对"） |
| 与派生查询的优先级 | 注解优先且旧条件告警 | `precedenceKeepsOrderAndPageable`（filter=注解内容；WARN 路径实现于 `MeiliAnnotatedQueries` criteria 短路） |
| （实现注记） | SpEL 变量语法 | `#{…}` 内以 `#name`/`#argN` 引用参数（Spring Data 惯例），已写入注解 javadoc 与映射指南 |

## module-build-foundation（delta）

| Requirement | 证据 |
|---|---|
| 多模块聚合结构（含 repository） | 根 pom modules；`mvn dependency:tree -pl spring-boot-starter-meili-orm` 无 repository/commons（7.1 复核，见 `enforcer-1.3.txt`） |
| commons 隔离构建护栏 | 违规形态=starter 临时加 repository 依赖 → enforcer 红（banned 链定位）；三模块 passed——`evidence/enforcer-1.3.txt` |
| 发布面五产品模块 | repository pom 无 deploy.skip、it/examples 保持 skip（既有声明覆盖新 it 侧） |

## dual-boot-compatibility-matrix（delta）

| Requirement | 证据 |
|---|---|
| 双代装配与 CRUD 往返等价（+Repository IT） | `MeiliRepositoryMatrixIT` 双模块各 3 条全绿（`evidence/matrix-5.2.txt`）；双代命中集合一致 |
| 矩阵版本哨兵（+commons 结构哨兵） | `generationSentinels`（`org.springframework.data.core.PropertyPath` 存在性：boot3 期望不存在/boot4 期望存在）——commons 制品无公开版本类，结构断言为实测修正形态（delta 措辞已回写） |
| 矩阵源码双份复制 | Matrix* 三文件 diff：除哨兵期望值与注释代际标注外逐字节一致（5.2 核对输出） |

## starter-documentation（delta）

| Requirement | 证据 |
|---|---|
| README 装配与功能面（repository opt-in） | README "Repository 风格访问" 小节 + 功能表行 + `meili.repositories.enabled` 配置行 |
| 限制清单（Repository 六条） | docs/limitations.md §11–16 |
| 映射指南派生/注解章 | docs/mapping-guide.md "Repository 层" 章（支持/不支持两清单与 `MeiliMethodNames` 实现逐字对照；预检修复指引样例） |
| 升级说明 commons 义务 | docs/boot3-to-boot4.md 检查清单 E 段（含矩阵结构哨兵判据） |

## 门禁与出口命令

- `bash scripts/check-source-citations.sh --selftest` → SELFTEST OK；全量扫描 → OK（含新模块 src/main 与 pom description）
- javadoc 门禁（show=private + failOnWarnings）：repository 模块全量通过
- `mvn -s /home/lam/repo/settings.xml clean verify`：见 7.1 运行记录

## 7.1 出口运行记录（2026-09-26）

- `mvn -s /home/lam/repo/settings.xml clean verify` → **BUILD SUCCESS**（1:22）：
  14 模块全绿——core / autoconfigure / starter / jackson3 / **repository** / it-boot3 /
  it-boot4 / it-boot4-jackson3 / examples×3 及聚合。
- `dependency:tree -pl spring-boot-starter-meili-orm`：`org.springframework.data:*` 与
  `meili-orm-repository` 计数 **0**（opt-in 纪律 + enforcer 双证）。
- `dependency:tree -pl meili-orm-repository`：`spring-data-commons:jar:3.5.13:compile`
  （最低支持代编译基线钉死）。
- repository 模块测试面：95 单元 + 1 真机 IT；矩阵 Repository IT 双代各 3 条。
