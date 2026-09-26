# repository-layer Specification

## Purpose
定义 Repository 层能力契约：`MeiliRepository` 接口族、工厂装配与模块自注册自动配置，把 Spring Data 风格的声明式仓库访问建立在既有 `MeiliSearchOperations` 闭环之上——全部数据通路经 Operations 委托，回调、序列化与任务等待语义零重复实现。

## Requirements

### Requirement: MeiliRepository 接口与 CRUD 落点

系统 SHALL 提供 `MeiliRepository<T, ID>`（继承 `org.springframework.data.repository.CrudRepository<T, ID>` 与 `ListPagingAndSortingRepository<T, ID>`）及其默认实现 `SimpleMeiliRepository`，各方法 SHALL 委托 `MeiliSearchOperations` 完成：`save`/`saveAll` 为 upsert（主键缺失报错同 Operations 契约）；`findById`/`existsById`/`deleteById`/`delete(entity)` 走单文档通道（非搜索通道）；`count()` 走 stats 端点；`deleteAll()` 清空绑定索引全部文档（不删索引）。`saveAll(Iterable)` SHALL 单请求批量。`deleteAll(Iterable)` v1 SHALL 逐条单文档删除（每实体一个请求），其代价与后续批量化取向记入限制文档。

#### Scenario: 仓库 CRUD 真机往返

- **WHEN** Testcontainers 真机上下文中注入 `MeiliRepository` 执行 save→findById→count→deleteById→findById
- **THEN** save 后按 `wait-task` 语义可查，findById 命中 Long 主键逐位无损的实体，deleteById 后 findById 为空

#### Scenario: 批量删除逐条生效

- **WHEN** `deleteAll(List.of(a, b))` 且 `wait-task=true`
- **THEN** 两文档均从索引消失，无残留

### Requirement: 无界读取与分页总数语义

`findAll()`（无参）SHALL 经 documents/fetch 通道读取，受服务端 `pagination.maxTotalHits`（默认 1000）约束：命中上限时 SHALL 截断返回并以 WARN 日志声明截断事实，不得静默假称全量。`findAll(Pageable)` SHALL 经搜索通道（空 q 浏览）返回 `Page<T>`，其中 `getTotalElements()`/`getTotalPages()` 取自 `estimatedTotalHits`/`totalHits` 的可用值——为估算语义，服务端不给出精确总数时不得伪装精确。

#### Scenario: findAll 截断可观测

- **WHEN** 索引文档数超过 maxTotalHits 时调用 `findAll()`
- **THEN** 返回条数等于上限且日志含截断 WARN

#### Scenario: 分页总数为估算值

- **WHEN** `findAll(PageRequest.of(0, 10))` 返回 `Page`
- **THEN** hits 分页正确，`getTotalElements()` 与服务端响应的估算总数字段一致

### Requirement: 工厂装配与元数据桥接

系统 SHALL 提供仓库工厂（`FactoryBean + InvocationHandler` 自持代理形态；经 spike 实证**不得继承** commons `RepositoryFactorySupport`/`RepositoryFactoryBeanSupport`——其钩子签名在 commons 两代间删改，继承即代际绑定）：目标仓库实例为绑定 `MeiliSearchOperations` 的 `SimpleMeiliRepository`；`EntityInformation<T, ID>`（commons 双代零漂移接口）SHALL 由 core 元模型（`MeiliPersistentEntity`）适配产出（主键类型与属性名取自 `@MeiliId` 声明），不得引入第二套持久类注解解析。方法到查询的解析表 SHALL 在工厂/代理构造期一次性建立（启动期完成派生与注解校验）。仓库代理 SHALL 为上下文单例、构造后不可变、并发安全。

#### Scenario: 主键类型解析一致

- **WHEN** 对 `@MeiliId Long id` 实体获取仓库并调用 `deleteById(1L)` 与错误类型 id
- **THEN** 正常类型走单文档删除；主键元模型与实际 `@MeiliId` 类型一致，无独立注解源

### Requirement: 启用路径与自动配置自注册

`meili-orm-repository` 模块 SHALL 自带 `AutoConfiguration.imports` 注册 `MeiliRepositoriesAutoConfiguration`，不依赖该模块进入 starter 聚合坐标。启用路径二选一等价：用户显式 `@EnableMeiliRepositories`（`basePackages` 缺省为注解所在类包，标准 commons 语义）；或未声明注解时由自动配置兜底扫描 `AutoConfigurationPackages` 注册仓库 bean。自动配置条件链 SHALL 为：`MeiliRepository` 类在 classpath、容器存在 `MeiliSearchOperations` bean、`meili.repositories.enabled` 缺省或为 true、且容器不存在用户自定义 `RepositoryFactoryBean`（任一不满足则整体不启用，即 back-off）。`meili.repositories.enabled` 属性 SHALL 在本模块 `spring-configuration-metadata.json` 在册（默认 true、类型 Boolean、文档说明）。

#### Scenario: 加依赖零配置即用

- **WHEN** 应用 classpath 含 repository 模块与 starter、声明一个 `MeiliRepository` 子接口且未写任何启用注解
- **THEN** 上下文注册该仓库 bean 且可注入

#### Scenario: 开关与自定义工厂退避

- **WHEN** `meili.repositories.enabled=false`，或用户已声明自己的 `MeiliRepositoryFactoryBean`
- **THEN** 本模块自动配置不注册任何仓库相关 bean

#### Scenario: 未引入模块的应用零扰动

- **WHEN** 仅引 starter（不含 repository 模块）的应用上下文启动
- **THEN** 既有客户端/数据/索引初始化自动配置行为与引入本 change 前一致，且依赖树不含 `org.springframework.data:*`

### Requirement: 委托链路透明语义

仓库方法 SHALL 继承 Operations 的全部横切语义：写路径 `BeforeConvertCallback`/`AfterSaveCallback` 与读路径 `AfterLoad`/`AfterConvert` 回调链照常触发；`meili.wait-task` 对仓库写/删方法同样生效；异常族（`MeiliOrmException`/`MeiliIndexAccessException`/`MeiliTaskTimeoutException`）不做仓库层重新包装。

#### Scenario: 回调经仓库生效

- **WHEN** 容器注册 `BeforeConvertCallback<Book>` 后经 `repository.save(book)` 写入
- **THEN** 回调修改落在发往服务端的文档上
