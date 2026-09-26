# Tasks

## 1. 模块骨架与桥接定案（spike 前置）

- [x] 1.1 新建 `meili-orm-repository` 模块（pom：依赖 core + autoconfigure + spring-data-commons（3.5.13 由父 BOM 解析）+ spring-context/beans/expression；javadoc/citation 门禁自动继承），根 pom `<modules>` 追加，`package-info.java` 占位；验证：`mvn -s /home/lam/repo/settings.xml -q clean verify` 全 reactor 绿（含新模块空构建）
- [x] 1.2 联网首拉 spring-data-commons 3.5.13 并核对解析版本；验证：`mvn -s /home/lam/repo/settings.xml dependency:tree -pl meili-orm-repository` 中 commons 恰为 3.5.13（失败=网络/镜像问题即停下报告，不改默认 settings）
- [x] 1.3 core/autoconfigure/starter 三模块 pom 加 maven-enforcer `bannedDependencies(org.springframework.data:*)`；验证：给 starter 临时加 repository 依赖→构建红且输出定位为被 ban 坐标，移除后 `clean verify` 绿（两形态截图/输出留档到本 change 目录）
- [x] 1.4 **spike：commons PartTree 桥可行性定案**——原型：core 元模型桥 + `PartTree` 解析 20 个典型方法名黄金断言（含改名属性/嵌套链/Not 嵌套/topN/OrderBy 组合）在 3.5.13 编译运行通过；同源码片段在 it-boot4（commons 4.0.3 运行期）执行无链接错误；六锚点类（`RepositoryFactorySupport`/`RepositoryFactoryBeanSupport`/`RepositoryConfigurationExtensionSupport`/`QueryMethod`/`EntityInformation`/`PropertyPath`）两代 javap 签名 diff 归档；验证：结论（含异常原文与决策树走向：单模块 or 薄变体 or 降级路线 b）写入本 change 目录 spike 记录，design D-2 据实回写

## 2. 装配层：接口、工厂与自动配置（repository-layer）

- [ ] 2.1 `MeiliRepository<T,ID>`（extends Crud+ListPagingAndSorting）与 `SimpleMeiliRepository`：save/saveAll/findById/existsById/count/deleteById/delete/deleteAll(Class)/findAll() 截断+WARN/findAll(Pageable) 估算总数/deleteAll(Iterable) 逐条；验证：mock `MeiliSearchOperations` 的 L1 委托断言全绿（含截断 WARN、逐条 N 请求计数、In 空集合不属本组）
- [x] 2.2 `EntityInformation` 适配（读 core 元模型 `@MeiliId`）+ `MeiliRepositoryFactory` + `MeiliRepositoryFactoryBean`；验证：工厂直接构造仓库实例的 L1 测试（主键类型一致、代理单例）
- [x] 2.3 `@EnableMeiliRepositories` + Registrar + `MeiliRepositoryConfigurationExtension`（含扩展点识别 `MeiliRepository` 子接口）；验证：纯 Spring 上下文测试——注解扫描出仓库 bean、无注解时不注册
- [x] 2.4 `MeiliRepositoriesAutoConfiguration`（条件链：`@ConditionalOnClass(MeiliRepository)`、`@ConditionalOnBean(MeiliSearchOperations)`、`meili.repositories.enabled` matchIfMissing、用户 `RepositoryFactoryBean` back-off）+ 模块自带 `AutoConfiguration.imports` + `additional-spring-configuration-metadata.json`；验证：L2 `ApplicationContextRunner` 断言——启用/开关退避/用户工厂 back-off/无 operations 整体静默退避/imports 逐行 `Class.forName` 可加载
- [ ] 2.5 委托透明语义：回调链与 `wait-task` 经仓库生效的 L1+真机断言（`BeforeConvertCallback` 修改落到发出文档；`wait-task=true` 时 save 后可查）；验证：对应测试绿
- [x] 2.6 提交本组：conventional commit（feat(repository): 接口/工厂/自动配置）

## 3. 派生查询：桥接、翻译与启动预检（repository-derived-queries）

- [x] 3.1 属性解析正式化：spike 原型（`SpikeNameParser`+`SpikePropertyResolver`）落入 src/main（反射字典最长前缀切分 + `@MeiliField.name`/点路径/`@JsonIgnore` 排除/查无属性启动异常含方法名属性段类名；spike 测试类保留为哨兵）；验证：L1 黄金断言（改名、嵌套、record、未知属性、缩写错误消息含"不支持缩写"形态）
- [x] 3.2 翻译器：自研语法解析结果→`MeiliQuery` 关键字映射全表（Equals/In（空集合短路不发请求）/Between/LessThan 系/Before/After/True/False/Not 括号/And/Or 嵌套/Containing+Like→q+attributesToSearchOn/OrderBy/topN）；值字面量渲染共用转义器；验证：L1 每关键字 ≥1 正例黄金串断言 + 空 IN 零请求断言
- [x] 3.3 启动期角色预检：filter→filterable、sort→sortable、Containing→searchable，缺失 `MeiliMappingException` 消息含双修复指引；判定源=实体声明（透传不进入输入，失败消息提示透传可能）；`null` 参数属性条件启动拒绝；验证：L1——缺角色启动失败/补齐成功/透传声明仍失败三形态断言
- [x] 3.4 `Pageable` 与返回形态：page=pageNumber+1/hitsPerPage=pageSize、方法名 OrderBy 优先 Pageable sort 追加、`List`/`Optional`（多取首+DEBUG）/`Page`；验证：L1 换算黄金断言
- [x] 3.5 不支持面：StartingWith/EndingWith/Regex/Null/Empty/IgnoreCase/属性缩写/Distinct 修饰符/集合属性等值/DTO/Stream → 引导期 `IllegalArgumentException` 列方法名与支持面；验证：L1 每类一条异常形态断言（消息可定位）
- [x] 3.6 真机 L3：Testcontainers v1.49.0 上全注解实体——派生查询各关键字命中集合与手写 `MeiliQuery` 等价断言、Not 对缺失字段行为钉死并回写注释、Containing 中文命中；验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-repository verify` 全绿

## 4. 注解查询（repository-query-annotation）

- [ ] 4.1 `@MeiliQuery` 注解面（q/filter/distinct，全空启动报错）+ 执行通道（search、回调、Pageable）；验证：L1 委托断言 + 真机 q×filter 组合交集命中断言
- [ ] 4.2 参数绑定：`?n` 位置 + `:name`（@Param/编译参数名）双轨、SpEL 求值（失败包装含表达式原文与方法名）、`Pageable`/`Sort` 参数不入占位计数；验证：L1 绑定黄金用例（含越界 `?5`、缺失 `:missing` 启动报错）
- [ ] 4.3 字面量转义器（String 自动包引号+转义 `"`/`\`，数值/布尔裸写，q 不转义）+ 注入用例集；验证：L1——值 `科幻" OR price > 0 --` 渲染后 DSL 结构不变、含括号/反斜杠/中文边界用例
- [ ] 4.4 启动期模板校验（括号配对、占位符可绑定性）与派生优先级（注解短路条件、保留 OrderBy/top、被忽略片段 WARN）；验证：L1 不配对报错定位 + 混合方法 WARN 断言

## 5. 双代矩阵（L4）

- [ ] 5.1 it-boot3/it-boot4 各加 repository 测试依赖与 `MeiliRepositoryIT`（源码复制式，除哨兵外逐字节一致）：save→派生（等值/IN/Between/Containing/OrderBy）→分页→`@MeiliQuery`→deleteById 真机往返，两代命中集合与顺序一致断言；哨兵追加 `SpringDataPackageVersion`（boot3 侧 `3.5.`、boot4 侧 `4.0.`）；验证：`mvn -s /home/lam/repo/settings.xml -q -pl it/meili-orm-it-boot3,it/meili-orm-it-boot4 -am verify` 双绿
- [ ] 5.2 diff 核对两模块 IT 源文件（仅哨兵期望值差异）；验证：diff 输出归档到本 change 目录

## 6. 文档（starter-documentation）

- [ ] 6.1 `docs/mapping-guide.md` 增"派生查询与注解查询"章：关键字支持/不支持两清单（与 3.2/3.5 实现逐字一致）、投影名桥接规则、`@MeiliQuery` 模板语法与转义规则、角色预检修复指引示例；验证：按指南任一行写方法名跑 L1 可复算；不支持清单一侧确实启动报错
- [ ] 6.2 `docs/limitations.md` + README 限制小节追加 Repository 六条（估算总数/maxTotalHits 截断/Containing 近似/逐条批量删/关键字子集与预检/无投影、Stream、异步），各给 workaround；README 装配小节加 repository 坐标与仓库样例（明示"不在 starter 内"）；`docs/boot3-to-boot4.md` 升级清单纳入 commons 钉版（三类→四类）与 repository 两代兼容面说明；验证：清单逐条对照测试/代码事实无超售
- [ ] 6.3 citation/javadoc 门禁自查：新模块 src/main 与 pom `<description>` 过 `bash scripts/check-source-citations.sh --selftest` 与全量扫描零命中；验证：脚本零退出

## 7. 集成核对（出口）

- [ ] 7.1 全 reactor 出口：`mvn -s /home/lam/repo/settings.xml clean verify` 全绿（新模块 + 既有 spike 哨兵 + 双矩阵含 Repository IT + examples 零扰动）；`dependency:tree` 复核 starter 无 commons、repository 钉 3.5.13；验证：命令输出留档，specs 三份新能力逐 Requirement 打勾核对表写入本 change 目录
- [ ] 7.2 提交出口记录（docs/openspec：核对表与 spike 结论），并确认 design D-2 已按 spike 实际结果落稿（若走了薄变体/路线 b，本 change 工件同步修订后再归档）
