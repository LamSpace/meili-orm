[English](../boot3-to-boot4.md)

# Spring Boot 3 → 4 升级说明（meili-orm 视角）

## 结论先行

同一个 `spring-boot-starter-meili-orm` jar 同时运行于 Boot 3.5.x 与 4.x：应用从 3.5 升级到
4.x 时 **meili-orm 无需换坐标、无需改配置**（属性前缀 `meili.*` 与
`AutoConfiguration.imports` 机制两代一致）。唯一的选择点是是否引入 Jackson 3 序列化模块。

## 双代兼容是怎么成立的

- **core 零 Spring 依赖**：注解、元模型、序列化抽象、查询 IR、Operations 全在纯 Java 层，
  不接触任何代际 API；
- **autoconfigure 只引用两代稳定的装配底座**（`@ConfigurationProperties`、`@ConditionalOn*`、
  `ObjectProvider`、imports 机制），编译基线取最低支持代 Boot 3.5.16，字节码基线 Java 17；
- **序列化换代隔离**：Boot 4 容器 ObjectMapper 是 Jackson 3（`tools.jackson`），主模块的
  默认序列化器基于 Jackson 2（找不到容器 bean 时自建实例，行为见
  [限制清单](limitations.md)第 4 条），Jackson 3 语义由可选模块接管；
- **护栏是编译事实**：`it/meili-orm-it-boot3`（钉 3.5.16）与 `it/meili-orm-it-boot4`
  （钉 4.0.3）各持一份逐字节一致的 starter IT（仅版本哨兵期望值不同），加
  `it/meili-orm-it-boot4-jackson3`（opt-in 接管验证），在根 `mvn clean verify` 中各自
  独立编译运行真机容器。误引单代特有 API 或 BOM 钉版漂移都会在双代矩阵当场红——
  每个矩阵模块都有 `SpringBootVersion` 哨兵断言自证"确实跑在被钉住的那一代"。

### 编译基线的一个有意例外

`meili-orm-serializer-jackson3` 是全工程唯一以 Boot 4.0.3 BOM 为编译基线的产品模块——
`tools.jackson` 坐标只有该代 BOM 管理。这不是纪律破坏：Boot 3 应用 classpath 上没有
`tools.jackson`，模块的条件装配（类存在性，经字节码元数据评估、不触类加载）使其对
Boot 3 完全惰化；矩阵中的 it-boot3 与基线 it-boot4 持续证明"类缺席则不接管"。

## 升级到 Boot 4 后的行为差异

| 方面 | Boot 3.5.x | Boot 4.x |
|---|---|---|
| 默认序列化器 | Jackson 2（优先采用容器 ObjectMapper bean） | Jackson 2 自建实例（容器 Jackson 3 mapper 不被采用） |
| 想要 Jackson 3 语义 | 不适用 | 加 `meili-orm-serializer-jackson3` 依赖即接管；或自注册 `MeiliDocumentSerializer` bean |
| Web 层序列化 | Jackson 2 | Jackson 3（与 meili-orm 读写通道相互独立——实体文档通道始终经 `MeiliDocumentSerializer`） |

Jackson3 模块用法：

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>meili-orm-serializer-jackson3</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

接管后行为与 Jackson 2 实现逐项等价（改名桥接、Long 精度、ISO 日期、未知键宽容——
镜像断言集常驻验证）；容器如已定义 Jackson 3 `ObjectMapper` bean，以它为基础配置。

## Repository 层与 spring-data-commons

`meili-orm-repository` 是全工程唯一依赖 `spring-data-commons` 的产品模块：其 commons
版本经 Boot 3.5.16 BOM（最低支持代编译基线）解析，运行期直接使用宿主 Boot 代际自带的
commons 版本，同一 jar 两代通用，不设 boot3/boot4 变体。两代运行时兼容由双代矩阵证明
——逐字节一致的 repository IT 在两代各自钉定的 classpath 上运行，并配合 commons
结构哨兵断言 4.0 特有类（如 `org.springframework.data.core.PropertyPath`）的存在/缺席
与被测代际预期精确一致，repository 层所依赖的 commons 面一旦漂移即矩阵当场红。

## 维护者

维护者侧的依赖升级义务（变更上述钉版及每次变更所要求的验证）见
[CONTRIBUTING.md](../../CONTRIBUTING.zh-CN.md)。
