# Proposal

## Why

M0–M3 已交付"零 Spring core + 自动配置 + starter"的模板化 Operations 闭环并通过双代矩阵，但用户访问 MeiliSearch 仍需手写 `MeiliQuery`/`MeiliSearchOperations` 调用；类 Spring Data 的 Repository 抽象是设计基线既定的最后一块功能面（决策基线"核心三件套 + 索引自动初始化 + Repository（后置里程碑）"）。本 change 落地 Repository 层：声明式接口、方法名派生查询、`@MeiliQuery` 注解查询，并把 `spring-data-commons` 的引入严格隔离在单模块内。

## What Changes

- 新增模块 `meili-orm-repository`：全工程唯一允许依赖 `org.springframework.data:spring-data-commons` 的模块，编译基线钉 commons 3.5.13（Boot 3.5.16 管理版本），Boot 4.0.3 侧由矩阵运行期验证。
- 新增 `MeiliRepository<T,ID>`（extends `CrudRepository` + `ListPagingAndSortingRepository`）、`SimpleMeiliRepository`（委托既有 `MeiliSearchOperations`，回调/序列化/wait-task 链路零重复实现）、`MeiliRepositoryFactory` / `MeiliRepositoryFactoryBean`、`@EnableMeiliRepositories` + Registrar。
- 新增自动配置 `MeiliRepositoriesAutoConfiguration`：**随 repository 模块自带 `AutoConfiguration.imports` 自注册**（对齐 `meili-orm-serializer-jackson3` 先例；此处偏离设计文档 §5.1 原"置于 autoconfigure 模块"的位置——若置于 autoconfigure 则 commons 隔离纪律被编译依赖击穿，裁决记录于 design.md），受 `meili.repositories.enabled`（默认 true）控制；starter 聚合坐标**不含** repository 模块，用户显式加依赖即得 Repository 能力。
- 派生查询：自研方法名语法解析（spike 实证 commons `PartTree` 的 `Part.getProperty()` 返回类型在两代迁移、桥路线不可双代，见 design D-2）→ 既有 `MeiliQuery` IR（等值/IN/区间/比较/True/False/Containing→q+attributesToSearchOn/Not/And/Or/top/OrderBy + `Pageable`），属性段经实体反射字典切分并落到 `@MeiliField.name` 与嵌套点路径（缩写不支持）；启动期角色预检（filter/sort 目标属性未声明对应角色 → 启动失败）。
- 注解查询：`@MeiliQuery(filter/q 模板, SpEL `:name` 与位置 `?0` 参数绑定, 字符串参数按 Meili DSL 转义)。
- 构建护栏：core/autoconfigure/starter 三模块加 maven-enforcer `bannedDependencies`（禁 `org.springframework.data:*`），使 commons 隔离成为构建事实。
- 双代矩阵与文档扩展：it-boot3/it-boot4 各增内容一致的 Repository IT；mapping-guide 增派生查询章、limitations 增 Repository 语义限制、升级检查清单纳入 commons 钉版。
- 纯增量：不修改 core/autoconfigure/starter 的任何公开 API 与既有装配行为；`spring-boot-starter-meili-orm` 与 examples 依赖树零变化。

## Capabilities

### New Capabilities

- `repository-layer`：Repository 接口族与工厂装配——`MeiliRepository` 契约、CRUD 各方法到 Operations 的落点语义（`findAll()` 上限截断、`Page` 总数为估算值、批量删 v1 逐条实现）、`@EnableMeiliRepositories` 与自动配置自注册、back-off 与 `meili.repositories.enabled`。
- `repository-derived-queries`：方法名派生查询——关键字到 `MeiliQuery` 的映射表与逐条语义（含 Containing→q 近似、Not 的缺失字段行为）、反射字典属性切分与投影名桥接规则（`@MeiliField.name`/嵌套点路径 ↔ Java 属性、缩写不支持）、`Pageable/Sort` 换算、启动期角色预检、不支持关键字的启动期报错契约。
- `repository-query-annotation`：`@MeiliQuery` 注解查询——filter/q 模板语法、参数绑定双轨（`?0` / SpEL `:name`+`@Param`）、字符串转义注入防护、与派生查询的互斥优先级、启动期模板合法性校验。

### Modified Capabilities

- `module-build-foundation`：模块清单与发布面纳入 `meili-orm-repository`（发布产品模块 4→5）；新增"commons 隔离 enforcer 护栏"要求（core/autoconfigure/starter 依赖树含 `org.springframework.data:*` 即构建失败）。
- `dual-boot-compatibility-matrix`：矩阵用例面纳入 Repository IT（双代各一轮：派生查询 + 分页 + `@MeiliQuery` 真机往返）；版本哨兵扩展 commons 运行时版本断言（3.5.x 侧 / 4.0.x 侧各一），钉定漂移可定位。
- `starter-documentation`：mapping-guide 增"方法名派生查询"章（关键字支持/不支持对照表与实现一致）、limitations 增 Repository 语义限制条目（估算总数 / fetch 上限 / Containing 近似 / 批量删逐条请求）且只转录已验证行为、boot3-to-boot4 升级检查清单纳入 commons 钉版变更义务。

## Impact

- **新增代码模块**：`meili-orm-repository/`（主源码 + L1/L2/L3 测试 + 自带 imports 与 configuration-metadata 资源）；根 pom `<modules>` 追加。
- **新增依赖**：repository 模块 → spring-data-commons（3.5.13 编译基线）、spring-context/spring-beans/spring-expression（由 Boot BOM 管理）；core/autoconfigure/starter 依赖树零变化。
- **矩阵模块**：it-boot3/it-boot4 各增 `meili-orm-repository` 测试依赖与一个 IT 类（源码复制式，差异仅哨兵期望值）。
- **环境**：本机 Maven 仓库当前仅有 commons 4.0.3，commons 3.5.13 及其 POM 链需首拉（联网操作，列入前置事项）。
- **文档**：README 功能表、mapping-guide、limitations、boot3-to-boot4 四份既有材料扩展；无发布元数据变更（repository 模块 `<description>` 受内部引用门禁扫描）。
- **不受影响**：`spring-boot-starter-meili-orm` 聚合坐标、examples 双 demo、既有 spike 哨兵、core 公开 API。
