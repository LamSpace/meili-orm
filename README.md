# meili-orm

MeiliSearch 官方 Java SDK（`com.meilisearch.sdk:meilisearch-java`）之上的类 Spring Data
Elasticsearch 风格 Spring Boot Starter：注解声明式映射、settings 投影自动同步、模板化
Operations、自动配置——**同一个 jar 同时兼容 Spring Boot 3.5.x 与 4.x**。

- 构建 JDK 25，字节码基线 Java 17（`maven.compiler.release=17`）
- MeiliSearch 服务端：v1.x；集成测试与演示钉 **v1.49.0**
- 许可证：Apache License 2.0

## 装配

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
  wait-task: true          # 写后可查（写操作默认异步任务）
```

## 用起来

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

启动期 `IndexInitializer` 按 `meili.index.auto-init` 自动建索引并推送注解投影出的
settings；漂移处置策略见[映射指南](docs/mapping-guide.md)。

### Repository 风格访问（opt-in 坐标）

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

方法名的属性名按实体投影规则桥接（`title` → `book_title`）；filter/sort 目标属性必须
已声明对应角色，否则**启动即失败**并给出修复指引。关键字支持/不支持全表、角色预检细则
见[映射指南](docs/mapping-guide.md)，语义边界见[限制清单](docs/limitations.md)第 11–16 条。

## 功能面

| 能力 | 说明 |
|---|---|
| 注解映射 | `@MeiliDocument` / `@MeiliId` / `@MeiliField`（角色声明）/ `@CreatedDate` / `@LastModifiedDate`（时间戳审计，见功能表下一行）/ `@MeiliSetting`（settings 透传），字段排除用 Jackson `@JsonIgnore` |
| Settings 投影 | "映射"的落地形态：角色注解 → searchable/filterable/sortable/displayedAttributes；铁律**不标注=不声明** |
| 索引自动初始化 | `auto-init=none / create-if-missing / sync-settings` × `on-settings-drift=warn / apply / fail` |
| 模板 Operations | `save / saveAll / findById / findAll / deleteById / deleteAll / count`；`search / multiSearch`；`indexExists / createIndex / deleteIndex / applySettings`；`awaitTask / getTask` |
| 强类型查询 IR | `MeiliQuery`（filter DSL/filterGroup、sort、limit-offset 与 page-hitsPerPage 两套分页、facets、matchingStrategy、distinct、hybrid、`raw` 逃生舱），SDK 类型不泄漏进业务代码 |
| 生命周期回调 | `BeforeConvert` / `AfterSave` / `AfterLoad` / `AfterConvert` 四件套，声明 bean 即生效 |
| 时间戳审计 | `@CreatedDate`（空值填充近似）/ `@LastModifiedDate`（每次覆盖）；六类型许可集（`Instant`/三时间类型/`long`/`Long`）非法类型启动失败；POJO 就地写回、record 重建；填充恒先于 `BeforeConvertCallback`；边界见限制清单第 17–18 条 |
| 可插拔序列化 | `MeiliDocumentSerializer` 接口；默认 Jackson 2 实现；Boot 4 场景可加 `meili-orm-serializer-jackson3` 模块接管 |
| Repository 层（opt-in） | 显式引入 `meili-orm-repository` 坐标即得 `MeiliRepository`：CRUD、方法名派生查询（等值/IN/区间/比较/布尔/Not/Containing→全文/OrderBy/TopN/分页）、`@MeiliQuery` 注解查询、启动期投影名桥+角色预检 fail-fast；starter 聚合不含该坐标，`meili.repositories.enabled` 默认开 |
| Testcontainers 集成（opt-in） | 显式引入 `meili-orm-testcontainers` 坐标即得 `MeiliSearchContainer` 类型化容器（钉版 v1.49.0、`/health` 就绪等待、镜像/密钥可覆盖）与 `@ServiceConnection` 一行注解桥接（仅产 `MeiliConnectionDetails`，用户自有 bean 时桥接退避；Boot 3.5.x/4.x 双代支持）；starter 聚合不含该坐标，详见 [Testcontainers 集成](docs/testcontainers.md) |
| 双代兼容护栏 | `it-boot3`（3.5.16）/ `it-boot4`（4.0.3）常驻编译运行矩阵 + 版本哨兵（含 commons 结构哨兵） |
| 异常体系 | `MeiliOrmException` 根；`MeiliMappingException`（启动期 fail-fast）/ `MeiliIndexAccessException`（服务端错误透传 code）/ `MeiliTaskTimeoutException` |

### 非目标（本版明确不做）

响应式（MeiliSearch SDK 为同步阻塞）、`@Version` 乐观锁、per-field 类型 mapping /
analyzer、nested 关联查询、SpEL 动态索引名、审计操作人（`@CreatedBy`/`@LastModifiedBy`）
与可插拔时钟（v1 时间戳审计为写路径系统时钟填充，见功能表）、连接/读超时配置项（SDK
硬约束，见限制清单）。

## 配置属性

| 属性 | 默认 | 说明 |
|---|---|---|
| `meili.enabled` | `true` | 总开关 |
| `meili.url` | `http://localhost:7700` | 服务地址 |
| `meili.api-key` | 空 | master key 或 API key |
| `meili.wait-task` | `false` | 写操作同步等待任务终态 |
| `meili.wait-timeout` | `5s` | 单次任务等待上限 |
| `meili.client-agents` | `meili-orm` | User-Agent 附加标识列表（`;` 分隔，追加在 SDK 自身版本 token 之后；配空值回退纯 SDK 默认） |
| `meili.index.auto-init` | `create-if-missing` | 建索引/同步策略（`none` / `create-if-missing` / `sync-settings`） |
| `meili.index.on-settings-drift` | `warn` | 漂移处置（`warn` / `apply` / `fail`；仅 `sync-settings` 会真正写入） |
| `meili.repositories.enabled` | `true` | 引入 `meili-orm-repository` 坐标后是否自动扫描注册仓库接口 |

有意不提供 `connect-timeout` / `socket-timeout`：官方 SDK 的 `Config` 内部自建
OkHttpClient、无注入口（见[限制清单](docs/limitations.md)）。

## 演示

`examples/` 下两个 demo（Boot 3.5.16 / 4.0.3 共用同一套业务字节码）覆盖导入、检索
（q+filter+sort+分页+facet）、单读、删除、回调与 raw 逃生舱全场景，一键流程与实测
curl 输出见 [examples/README.md](examples/README.md)。

## 构建与测试

```bash
mvn -s /home/lam/repo/settings.xml clean verify
```

> `-s /home/lam/repo/settings.xml` 为本机 Maven 仓库配置（镜像/本地库路径），换机器时
> 替换为自己的 settings 路径；本仓库 `.mvn/maven.config` 亦提供同路径兜底。

前置：Docker 守护进程可用 + 本地存在 `getmeili/meilisearch:v1.49.0` 镜像（core/autoconfigure
/双矩阵的集成测试经 Testcontainers 直连真实服务端；缺 Docker 时 IT 快速失败且错误可辨识）。
全量构建同时执行 Javadoc 完整度门禁（含私有成员）与源码引用门禁。
同一套验证已接入 CI：`.github/workflows/verify.yml` 在 master 推送与拉取请求上以带 Docker 的
执行器跑全 reactor `clean verify`（显式 `-s ci/settings.xml`）并复跑源码引用门禁。

## 文档

- [映射指南](docs/mapping-guide.md)：注解 → MeiliSearch 概念/settings 对照、投影管线、回调
- [限制清单](docs/limitations.md)：已实证限制与 workaround
- [Boot 3 → 4 升级说明](docs/boot3-to-boot4.md)：双代兼容策略、Jackson3 可选模块、依赖升级检查清单
- [Testcontainers 集成](docs/testcontainers.md)：类型化容器、`@ServiceConnection` 一行注解、手工桥接样例、双代支持矩阵
- [Spike 结论](docs/spikes.md)：读写通道架构决策的实证记录（JsonHandler 不兼容、raw 通道精度契约）
