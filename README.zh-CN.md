# Meili-ORM

<div align="center">

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE) [![CI](https://github.com/LamSpace/meili-orm/actions/workflows/verify.yml/badge.svg)](https://github.com/LamSpace/meili-orm/actions/workflows/verify.yml) [![Java](https://img.shields.io/badge/Java-17%2B-orange)](#-构建与测试) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x%20%7C%204.x-brightgreen)](docs/zh-CN/boot3-to-boot4.md) [![Meilisearch](https://img.shields.io/badge/Meilisearch-v1.x-ff59a1)](https://www.meilisearch.com/docs)

[English](README.md)

</div>

构建于 Meilisearch 官方 Java SDK（`com.meilisearch.sdk:meilisearch-java`）之上的、
类 Spring Data Elasticsearch 风格的 **Meilisearch Spring Boot Starter**：注解声明式映射、
settings 投影自动同步、模板化 Operations、自动配置——**同一个 jar 同时兼容 Spring Boot
3.5.x 与 4.x**。

## ⚡ 一览

给实体标上注解、注入 `MeiliSearchOperations`，即可检索。Starter 在启动期把字段角色注解
投影为 Meilisearch settings，把 SDK 类型挡在业务代码之外，`Long` 主键逐位无损
（raw JSON 通道），且一份构件横跨两代 Spring Boot。

**为什么不用裸 SDK？** SDK 给的是 HTTP 绑定，不给实体映射、settings 管理、任务感知的写语义
和 Spring 装配。**为什么不选 `spring-data-meilisearch`？** 那个社区项目重新实现了 Spring Data
内部机制；Meili-ORM 对齐 Spring Data Elasticsearch 的*编程模型*（Operations 模板 + 可选
repository 层），同时保持三方 starter 的更小契约面。

## 📦 装配

> **尚未发布到 Maven Central。** 首发之前请从源码安装：

```bash
git clone https://github.com/LamSpace/meili-orm.git
cd meili-orm
mvn -DskipTests install
```

然后引入 starter：

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>spring-boot-starter-meili-orm</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

三行配置即用：

```yaml
meili:
  url: http://localhost:7700
  api-key: masterKey-xxxxxxxx
  wait-task: true          # 写后可查（Meilisearch 写操作是异步任务）
```

## 🚀 用起来

```java
@MeiliDocument(indexName = "books")
public record Book(
        @MeiliId Long id,
        @MeiliField(name = "book_title", searchable = true, searchableOrder = 1) String title,
        @MeiliField(filterable = true) String genre,
        @MeiliField(filterable = true, sortable = true) Double price) {
}

@Service
class BookService {
    private final MeiliSearchOperations operations;      // 自动配置注入

    void importOne(Book book) {
        operations.save(book);                           // upsert；wait-task 下返回即可查
    }

    List<Book> search(String q) {
        return operations.search(MeiliQuery.query(q)
                .filter("genre = \"科幻\"")
                .sort("price:asc")
                .page(1).hitsPerPage(10)
                .facets("genre"), Book.class)
                .getHits();                              // Long 主键逐位无损（raw 通道）
    }
}
```

启动期 `IndexInitializer` 按 `meili.index.auto-init` 自动建索引并推送注解投影出的 settings；
漂移处置策略见[映射指南](docs/zh-CN/mapping-guide.md)。

### 🗂 Repository 风格访问（opt-in 坐标）

模板 Operations 之外，显式加入仓库坐标（**不在 starter 聚合内**，按需引入）：

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>meili-orm-repository</artifactId>
    <version><!-- 与其余 meili-orm 坐标同版 --></version>
</dependency>
```

接口即注册（应用包下自动扫描；亦可 `@EnableMeiliRepositories` 指定包）：

```java
public interface BookRepository extends MeiliRepository<Book, Long> {

    List<Book> findByGenreAndPriceGreaterThan(String genre, Double min);  // 派生 → filter DSL

    List<Book> findByTitleContaining(String t);                            // → 全文 q + 限定属性

    Page<Book> findPageByGenreOrderByPriceAsc(String genre, Pageable pg);  // 分页（总数为估算值）

    @MeiliQuery(filter = "price BETWEEN :lo AND :hi")                      // 注解逃生舱
    List<Book> inRange(@Param("lo") Double lo, @Param("hi") Double hi);
}
```

方法名的属性名按实体投影规则桥接（`title` → `book_title`）；filter/sort 目标属性必须已声明
对应角色，否则**启动即失败**并给出修复指引。关键字支持/不支持全表、角色预检细则见
[映射指南](docs/zh-CN/mapping-guide.md)，语义边界见[限制清单](docs/zh-CN/limitations.md)
第 11–16 条。

## ✨ 功能面

| 能力 | 一句话 | 详见 |
|---|---|---|
| 注解映射 | `@MeiliDocument` / `@MeiliId` / `@MeiliField` 角色 / `@MeiliSetting` 透传；字段排除用 `@JsonIgnore` | [映射指南](docs/zh-CN/mapping-guide.md) |
| Settings 投影 | 角色注解 → searchable/filterable/sortable/displayed 数组；铁律**不标注 = 不声明** | [映射指南](docs/zh-CN/mapping-guide.md) |
| 索引自动初始化 | `auto-init=none / create-if-missing / sync-settings` × `on-settings-drift=warn / apply / fail` | [映射指南](docs/zh-CN/mapping-guide.md) |
| 模板 Operations | 文档 CRUD、search/multiSearch、索引与 settings 管理、任务等待 | `MeiliSearchOperations` javadoc |
| 强类型查询 IR | `MeiliQuery`：filter DSL 与分组、sort、两套分页、facets、hybrid、`raw` 逃生舱——SDK 类型零泄漏 | `MeiliQuery` javadoc |
| 生命周期回调 | `BeforeConvert` / `AfterSave` / `AfterLoad` / `AfterConvert`，声明 bean 即生效 | [映射指南](docs/zh-CN/mapping-guide.md) |
| 时间戳审计 | `@CreatedDate`（空值填充）/ `@LastModifiedDate`（每次覆盖）；非法类型启动失败 | [限制清单](docs/zh-CN/limitations.md) 第 17–18 条 |
| 可插拔序列化 | `MeiliDocumentSerializer` 接口；默认 Jackson 2；Boot 4 可加 Jackson 3 模块接管 | [Boot 3 → 4 指南](docs/zh-CN/boot3-to-boot4.md) |
| Repository 层（opt-in） | `MeiliRepository`：CRUD、方法名派生、`@MeiliQuery`、启动期角色预检 | [映射指南](docs/zh-CN/mapping-guide.md) |
| Testcontainers 集成（opt-in） | 类型化 `MeiliSearchContainer` + `@ServiceConnection` 桥接集成测试 | [Testcontainers 指南](docs/zh-CN/testcontainers.md) |
| 双代兼容护栏 | Boot 3.5.16 / 4.0.3 常驻编译运行矩阵 + 版本哨兵 | [Boot 3 → 4 指南](docs/zh-CN/boot3-to-boot4.md) |
| 异常体系 | `MeiliOrmException` 根；映射错误启动期 fail-fast；服务端错误 code 透传 | `core.exception` javadoc |

### 🚫 非目标（明确不做）

响应式（Meilisearch Java SDK 为同步阻塞）、`@Version` 乐观锁、per-field 类型 mapping / 分词器、
nested 关联查询、SpEL 动态索引名、审计操作人（`@CreatedBy`/`@LastModifiedBy`）与可插拔时钟、
连接/读超时配置项（SDK 硬约束，见[限制清单](docs/zh-CN/limitations.md)）。

## ⚙️ 配置属性

| 属性 | 默认 | 说明 |
|---|---|---|
| `meili.enabled` | `true` | 总开关 |
| `meili.url` | `http://localhost:7700` | 服务地址 |
| `meili.api-key` | （空） | master key 或 API key |
| `meili.wait-task` | `false` | 写操作同步等待任务终态 |
| `meili.wait-timeout` | `5s` | 单次任务等待上限 |
| `meili.client-agents` | `meili-orm` | User-Agent 附加标识列表（`;` 分隔，追加在 SDK 自身版本 token 之后；配空值回退纯 SDK 默认） |
| `meili.index.auto-init` | `create-if-missing` | `none` / `create-if-missing` / `sync-settings` |
| `meili.index.on-settings-drift` | `warn` | `warn` / `apply` / `fail`（仅 `sync-settings` 会真正写入） |
| `meili.repositories.enabled` | `true` | 引入 opt-in 坐标后是否自动扫描注册仓库接口 |

有意不提供 `connect-timeout` / `socket-timeout`：官方 SDK 的 `Config` 内部自建 OkHttpClient、
无注入口（[限制清单](docs/zh-CN/limitations.md) 第 1 条）。

## 🎮 演示

[`examples/`](examples/README.zh-CN.md) 下两个演示工程（Boot 3.5.16 / 4.0.3 两个启动壳共用同一套
业务代码）覆盖导入、全链路检索（q + filter + sort + 分页 + facet）、单读、删除、回调与 raw
逃生舱——一键流程与真机 curl 转录见[演示工程说明](examples/README.zh-CN.md)。

## 📁 项目结构

多模块 Maven reactor（根 `pom.xml` 的 `<modules>`）。仓库 slug 保持小写 `meili-orm`，
"Meili-ORM" 为展示名。六个构件对外发布，其余为仅构建或工具目录。

| 目录 | 性质 | 内容 |
|---|---|---|
| `meili-orm-core/` | 发布 | 注解映射、实体元模型、序列化抽象、强类型查询 IR（`MeiliQuery`）、settings 投影、模板化 `Operations`——零 Spring 依赖 |
| `meili-orm-spring-boot-autoconfigure/` | 发布 | Spring Boot 自动配置：客户端装配、索引初始化与 settings 同步。一份 jar 兼容 Boot 3.5.x 与 4.x |
| `spring-boot-starter-meili-orm/` | 发布 | starter 聚合：core + autoconfigure + Boot base starter |
| `meili-orm-serializer-jackson3/` | 发布 · 可选 | 面向 Boot 4 的 Jackson 3 序列化模块 |
| `meili-orm-repository/` | 发布 · 可选 | 类 Spring Data 声明式仓库；唯一依赖 `spring-data-commons` 的模块，不被 starter 聚合 |
| `meili-orm-testcontainers/` | 发布 · 可选 | 类型化 Meilisearch 容器 + `@ServiceConnection` 集成测试桥接 |
| `it/` | 仅构建 | **集成测试**兼容矩阵（`boot3` / `boot4` / `boot4-jackson3` 子模块），不发布构件。目录名取 integration tests 首字母，非英文代词 it |
| `examples/` | 仅构建 | 双代演示：共享 `meili-orm-example-common` + `boot3` / `boot4` 启动壳 |
| `docs/` | 文档 | 各指南（`mapping-guide`、`limitations`、`boot3-to-boot4`、`testcontainers`）、`zh-CN/` 镜像、`internal/` 设计留档 |
| `openspec/` | 内部 | spec 驱动的变更管理（`specs/`、`changes/`） |
| `scripts/` | 工具 | 构建门禁脚本（`check-source-citations.sh`） |
| `ci/` | 工具 | CI 专用 Maven `settings.xml` |
| `etc/` | 工具 | 构建资源：license 门禁模板 `license-header.txt` |
| `.github/` | 工具 | GitHub Actions 工作流（`verify.yml`） |
| `.mvn/` | 工具 | Maven 命令行默认配置（`maven.config`） |

## 🧪 构建与测试

```bash
mvn clean verify
```

前置：可用的 Docker 守护进程 + 本地 `getmeili/meilisearch:v1.49.0` 镜像——core/autoconfigure/
矩阵的集成测试经 Testcontainers 直连真实服务端（缺 Docker 时 IT 快速失败且错误可辨识）。
全量构建同时执行三道门禁：私有成员 Javadoc 完整度、内部引用扫描、每个 Java 文件的
Apache-2.0 License 头检查。维护者本机的 Maven settings 差异（镜像/本地库路径）见
[CONTRIBUTING](CONTRIBUTING.zh-CN.md)。

## 📚 文档

- [映射指南](docs/zh-CN/mapping-guide.md) —— 注解 → Meilisearch 概念/settings、投影管线、回调、派生查询
- [限制清单](docs/zh-CN/limitations.md) —— 18 条已实证行为边界，各给 workaround
- [Boot 3 → 4 升级说明](docs/zh-CN/boot3-to-boot4.md) —— 双代兼容策略与 Jackson 3 可选模块
- [Testcontainers 集成](docs/zh-CN/testcontainers.md) —— 类型化容器、`@ServiceConnection`、手工桥接
- [演示工程](examples/README.zh-CN.md) —— 可运行 demo 与真机转录
- English docs: [README.md](README.md) · [docs/](docs/)

## 🤝 参与贡献

构建命令、三道门禁与源码语言/License 约定见 [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md)。

## ⚖️ 许可证

[Apache License 2.0](LICENSE)。构建于官方
[meilisearch-java](https://github.com/meilisearch/meilisearch-java) SDK 之上。
