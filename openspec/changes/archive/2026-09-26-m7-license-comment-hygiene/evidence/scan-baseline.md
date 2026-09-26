# m7 基线扫描与中文数据白名单（tasks 1.1 / 1.2）

扫描命令（复现口径）：

```bash
# 注释行（行首 //、*、/*）含 CJK
grep -cP '^[[:space:]]*(//|\*|/\*).*[\x{4e00}-\x{9fff}]' <file>
# 非行首注释含 CJK（其中混有三类：① 运行期消息字面量 ② 数据字面量 ③ 行尾注释——③ 亦须翻译）
grep -P '[\x{4e00}-\x{9fff}]' <file> | grep -vP '^[[:space:]]*(//|\*|/\*)'
```

统计：main 81 文件中 13 个含行首中文注释（~95 行）、28 个含非行首注释中文（消息/数据/行尾注释混合，~146 行）；test 95 文件中 83 个含行首中文注释（~524 行）、45 个含非行首中文（~322 行，含行尾注释噪声）；全部 pom XML 注释与 `<description>` 为中文；examples 两枚 `application.yml` 含中文注释。

## A. main 含行首中文注释（13）

repository/package-info、core/package-info、testcontainers/package-info、example-common{BookController×28, Author, Book×16, ExampleMeiliConfig}、example-boot3/boot4{MeiliExampleApplication}、repository/support/MeiliPropertyPaths、autoconfigure/package-info、core/internal/SdkMeiliRawGateway、jackson3/package-info

## B. main 含中文运行期消息（异常/日志，28 文件，约 146 行）

- core：MeiliPersistentEntity(17)、DefaultMeiliSearchOperations(6)、SdkMeiliRawGateway(4)、SdkQueryTranslator(5)、MeiliErrors(3)、MeiliAuditSupport(3)、MeiliEntityCallbacks(5)、MeiliSettingsProjection(4)、Jackson2DocumentSerializer(2)、MeiliNames(2)、MeiliTaskTimeoutException(1)、ProjectedSettings(1)、MeiliSearchResult(1)
- repository：MeiliAnnotatedQueries(23)、MeiliDerivedQueries(17)、MeiliPropertyPaths(10)、MeiliRepositoryProxy(5)、SimpleMeiliRepository(5)、MeiliMethodNames(6)、MeiliLiterals(4)、MeiliRepositoryFactoryBean(3)、MeiliEntityInformation(1)、两个 config/  Registrar/Scanner(各1)
- autoconfigure：MeiliIndexInitializer(12)、MeiliInitializationAutoConfiguration(1)、MeiliEntityScanner(1)
- jackson3：Jackson3DocumentSerializer(2)

## C. test 消息断言清单（随 B 翻译同步改，共 14 处）

| 文件 | 位置 | 现断言子串 |
|---|---|---|
| core mapping/MeiliPersistentEntityTest | 145 | `主键` |
| core operations/DefaultMeiliSearchOperationsSearchTest | 144 | `投影` |
| core operations/DefaultMeiliSearchOperationsWriteTest | 70,146 | `主键` |
| autoconfigure/MeiliIndexInitializerTest | 225 | `重建` |
| repository spike/SpikeDerivedNameBridgeTest | 183,197,205 | `无法解析属性段`/`聚合`/`缩写` |
| repository core/SimpleMeiliRepositoryTest | 166 | `截断` |
| repository query/MeiliAnnotatedQueriesTest | 132,136 | `至少提供`/`括号不配对` |
| it-boot4 spike/SpikeDerivedNameBridgeTest（repository 同文件矩阵副本） | 183,197,205 | 同上三处 |

跨模块协调：it-boot4 副本的 spike 断言（`无法解析属性段`/`聚合`/`缩写`）经核实其消息抛出点在 it-boot4 自己的 test 树内（SpikeNameParser/SpikePropertyResolver 为同模块自包含哨兵源，非 repository main），故不构成跨模块约束。主线收口：5 处（2 抛出点 + 3 断言）配对同译，全 it-boot4 spike 目录现零 CJK，与 repository 原件同断言子串的语义一致。

boot3 MeiliRepositoryMatrixIT 存量笔误（注释 `(this module is 4)` 而常量为 `"3."`）系原中文即有之，按忠实翻译保留，非 m7 引入——留待独立修订，不并入翻译 commit。

## D. 中文数据白名单（保留清单：文件 + 用途 + 代表字面量）

| 文件/资源 | 用途 | 代表字面量 |
|---|---|---|
| core test spike/SpikeAJsonHandlerIT | 通道实证样本 | 三体、刘慈欣 |
| core test spike/SpikeBRawJacksonPrecisionIT | CJK 无损往返契约锁定 | 三体、刘慈欣、北京、科幻、雨果奖 |
| core test operations/MeiliCoreIT | 真机全链路书目数据与检索词 | 三体、沙丘、银河帝国：基地、雨果奖、长篇、科幻 |
| core test internal/SdkQueryTranslatorTest | 查询 IR→filter 目标串中的值 | `genre = "科幻"`、`"历史"` |
| core test query/MeiliSearchResultTest | facet 统计值 | 科幻 |
| core test mapping/MeiliMappingContextTest、MeiliPersistentEntityTest | 样本实体字段值 | 三体 等 |
| core test serialize/Jackson2DocumentSerializerTest | 投影序列化锁定 | 活着 |
| jackson3 test Jackson3DocumentSerializerTest | 同上（镜像断言集） | 活着 |
| core test resources/golden/*.json | golden settings 数据 | synonyms/stopwords 中文项 |
| repository test MeiliRepositoryIT、MeiliRepositoryLifecycleTest、query/*Test | 派生/注解查询条件值与回写样本 | 三体!、科幻、银河帝国：基地 等 |
| testcontainers test MeiliServiceConnectionIT、MeiliSearchContainerIT | 容器读写样本 | 三体 |
| examples resources data.json、meili/books.json | 演示书目与中文 stopwords 设置 | 全部保留 |
| examples src/main java 中数据字面量（如有） | 演示返回值 | 逐处判定 |

判定总则：**字面量作为"被检索/被序列化/被断言的业务数据"→ 保留；作为"给人看的消息/注释"→ 译**。行尾注释属注释，一律译。

## 插件事实

`com.mycila:license-maven-plugin` Maven Central 当前 latest/release = **5.1.2**（设计 D2"取实施时最新稳定"条款落点）；aliyun 镜像可达，本机与 CI 均可解析。
