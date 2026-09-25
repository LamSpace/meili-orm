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
  默认序列化器基于 Jackson 2（找不到容器 bean 时自建实例，行为见限制清单第 4 条），
  Jackson 3 语义由可选模块接管；
- **护栏是编译事实**：`it/meili-orm-it-boot3`（钉 3.5.16）与 `it/meili-orm-it-boot4`
  （钉 4.0.3）各持一份逐字节一致的 starter IT（仅版本哨兵期望值不同），加
  `it/meili-orm-it-boot4-jackson3`（opt-in 接管验证），在根 `mvn clean verify` 中各自
  独立编译运行真机容器。误引单代特有 API 或 BOM 钉版漂移都会在矩阵当场红——
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

## 依赖升级检查清单（维护者义务）

meili-orm 的全部兼容承诺以"矩阵每次都被跑"为前提。**变更下列任一钉版后，必须完成对应
验证才算升级成立**；任何一步失败都按阻断缺陷处理，禁止给单侧模块换依赖树救绿。

### A. 升级 meilisearch-java（SDK 钉版）

```bash
# 1. 改根 pom <meilisearch-java.version> 后全 reactor 构建（含 core IT、autoconfigure IT、双矩阵）
mvn -s /home/lam/repo/settings.xml clean verify
```

2. 重读哨兵结论：`SpikeAJsonHandlerIT` 锁定的"自定义 JsonHandler 与 SDK 内部模型
   不兼容"与 `SpikeBRawJacksonPrecisionIT` 锁定的"raw→Jackson Long 精度契约"是否仍
   成立（两测试变红 = SDK 行为翻案，须修订读写通道决策并更新 `docs/spikes.md`，
   不得反向改断言迁就）；
3. 真机 demo 冒烟一轮（`examples/README.md` curl 集）。

### B. 升级 Boot 兼容代（3.5.x / 4.x 任一，改根 pom `<spring-boot.version>` 或
`it/*`、`examples/*` 模块内 BOM 版本）

```bash
# 1. 全 reactor 构建：双矩阵 + examples 装配冒烟 + 版本哨兵
mvn -s /home/lam/repo/settings.xml clean verify
# 2. 钉版核对：两矩阵模块各解析出唯一 Boot 版本
mvn -s /home/lam/repo/settings.xml dependency:tree -pl it/meili-orm-it-boot3 | grep org.springframework.boot | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | sort -u
mvn -s /home/lam/repo/settings.xml dependency:tree -pl it/meili-orm-it-boot4 | grep org.springframework.boot | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | sort -u
```

若新一代移除/搬迁了 autoconfigure 所依赖的装配底座（`@ConditionalOn*`、
`@ConfigurationProperties`、imports 机制），即触碰兼容边界，需要方案级评审。

### C. 升级 MeiliSearch 服务端钉版（改根 pom `<meili-server.version>` 与
`MeiliContainer` 镜像常量）

```bash
# 1. 先拉镜像，再全 reactor 构建——core/autoconfigure/矩阵全部 IT 直连新服务端
docker pull getmeili/meilisearch:v1.50.0   # 以目标版本替换
mvn -s /home/lam/repo/settings.xml clean verify
```

2. 真机 demo 冒烟一轮，重点核对：settings 投影各键、`documents/fetch`、stats 计数、
   filter 语法在新一代服务端的行为（限制清单第 2、6、8 条所依赖的事实是否漂移）；
3. 更新 README/限制清单中"钉 v1.49.0"的表述。

### D. 升级 Testcontainers / 演示依赖等构建面

根 `mvn clean verify` 全绿即可（矩阵与 demo 冒烟测试随 reactor 运行）；TC 钉版见
`it/pom.xml` `<testcontainers.version>`（Boot 4 BOM 不管理 TC，矩阵三代统一钉定）。
