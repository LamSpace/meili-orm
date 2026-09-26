# 参与 meili-orm 贡献

[English](CONTRIBUTING.md)

## 前置环境

- 构建用 **JDK 25**（字节码基线为 Java 17，`maven.compiler.release=17`——发布构件必须保持
  17 兼容）。
- **Maven 3.9+**。需要自定义 settings（镜像/本地库路径）时显式传入：
  `mvn -s /path/to/your/settings.xml ...`。本项目主力开发机为
  `-s /home/lam/repo/settings.xml`；CI 使用随仓库提交的、不含本机路径的
  [`ci/settings.xml`](ci/settings.xml)。
- **Docker** 守护进程 + 本地 `getmeili/meilisearch:v1.49.0` 镜像——集成测试经 Testcontainers
  直连真实 Meilisearch 服务端。Testcontainers 还需其 `ryuk` sidecar 镜像；网络无法直达
  Docker Hub 时请预拉取（或在 `~/.testcontainers.properties` 关闭 ryuk）。

## 构建

```bash
mvn clean verify
```

编译全部模块（产品模块、Boot 3.5.16 / 4.0.3 / Jackson3 三个矩阵、examples），执行单元与集成
测试，并强制下述三道门禁。矩阵与哨兵 IT 就是兼容性契约，没有旁路。

reactor 布局：产品模块 `meili-orm-core`、`meili-orm-spring-boot-autoconfigure`、
`spring-boot-starter-meili-orm`、`meili-orm-serializer-jackson3`、`meili-orm-repository`，
加上 opt-in 的 `meili-orm-testcontainers`；`it/` 兼容矩阵；`examples/` 演示工程。
repository 与 testcontainers 刻意**不**被 starter 聚合。

## 三道构建门禁

| 门禁 | 强制内容 | 满足方式 |
|---|---|---|
| **Javadoc 完整度** | `maven-javadoc-plugin` 配 `show=private` + `failOnWarnings`：`src/main` 的每个类/方法/字段（含私有）必须有 Javadoc | 补写 Javadoc；深度按契约分量分级（简单访问器保持简洁） |
| **内部引用扫描** | [`scripts/check-source-citations.sh`](scripts/check-source-citations.sh)：发布模块 `src/main` 注释与 pom `<description>` 不得引用内部过程材料（设计文档节号、里程碑/任务代号、openspec change 名） | 注释只读自洽；提交前跑脚本及 `--selftest` |
| **License 头** | `com.mycila:license-maven-plugin` 的 `check` 绑定 `validate`：`src/main`/`src/test` 全部 `**/*.java` 必须携带与 [`etc/license-header.txt`](etc/license-header.txt) 逐字一致的 Apache-2.0 头部（版权行 `Copyright 2026 the original author or authors.`） | `mvn com.mycila:license-maven-plugin:format` 自动插入 |

## 源码语言约定

仓库内全部注释与面向人的文本一律**英文**：Java 注释/Javadoc（main 与 test，含行尾注释）、
pom 的 XML 注释与 `<description>`、示例 `application.yml` 注释，以及 `src/main` 的运行期文本
（异常消息、日志文案）。承担**测试/演示数据**语义的中文字符串字面量（CJK 无损精度样本、演示
书目数据、golden 资源）是刻意保留项；豁免必须以变更实施材料中的显式枚举登记，不设隐式例外。
消息文本不是断言契约——消息与其测试断言同步翻译。

## 维护者依赖升级检查清单

本项目的全部兼容性承诺以"矩阵每次都被跑"为前提。**变更下列任一钉版后，必须完成对应验证才算
升级成立**；任何一步失败都按阻断缺陷处理，禁止给单侧模块换依赖树救绿。

### A. 升级 meilisearch-java（SDK 钉版，根 pom `<meilisearch-java.version>`）

```bash
mvn clean verify    # 全 reactor：core IT、autoconfigure IT、双矩阵
```

1. 重读哨兵结论：`SpikeAJsonHandlerIT` 锁定的"自定义 JsonHandler 与 SDK 内部模型不兼容"与
   `SpikeBRawJacksonPrecisionIT` 锁定的"raw → Jackson 的 `Long` 主键精度契约"是否仍成立
   （任一测试变红 = SDK 行为翻案：须回改读写通道决策并更新 spike 实证档案，不得反向改断言
   迁就）；
2. 真机 demo 冒烟一轮（examples README 的 curl 集）。

### B. 升级 Boot 兼容代（3.5.x / 4.x 任一——根 pom `<spring-boot.version>` 或 `it/*`、
`examples/*` 模块内 BOM 版本）

```bash
mvn clean verify
# 钉版核对：两矩阵模块各解析出唯一 Boot 版本
mvn dependency:tree -pl it/meili-orm-it-boot3 | grep org.springframework.boot | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | sort -u
mvn dependency:tree -pl it/meili-orm-it-boot4 | grep org.springframework.boot | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | sort -u
```

若新一代移除/搬迁了 autoconfigure 所依赖的装配底座（`@ConfigurationProperties`、
`@ConditionalOn*`、`ObjectProvider`、imports 机制），即触碰兼容边界，需要方案级评审。

### C. 升级 Meilisearch 服务端钉版（根 pom `<meili-server.version>` 与 `MeiliContainer`
镜像常量）

```bash
docker pull getmeili/meilisearch:v1.50.0   # 以目标版本替换
mvn clean verify
```

1. 真机 demo 冒烟一轮，重点核对 settings 投影各键、`documents/fetch`、stats 计数与 filter
   语法在新一代服务端的行为（限制清单第 2、6、8 条所依赖的事实是否漂移）；
2. 更新两份 README 与 `docs/limitations.md`（含 zh 镜像）中"钉 v1.49.0"的表述。

### D. 升级 Testcontainers / 演示依赖等构建面

根 `mvn clean verify` 全绿即可（矩阵与 demo 冒烟测试随 reactor 运行）；TC 钉版在 `it/pom.xml`
`<testcontainers.version>`（Boot 4 BOM 不再管理 Testcontainers，矩阵三代统一钉定）。

### E. 首次发布到 Maven Central（发布成立后）

发布坐标落地后：为 `README.md` / `README.zh-CN.md` 追加 Maven Central 版本徽章（装配小节现
写明"尚未发布"，须同步改写），并在 `CHANGELOG.md` 记录发布。

## 备注

- `docs/internal/` 存放非交付过程材料（设计文档、原始实施计划、spike 实证档案、未提交的上游
  issue 草稿）。交付文档不以它们为阅读前提；它们随仓库历史留存。
- 远程仓库 *About* 元数据（描述/Topics）在 GitHub Web UI 维护，定稿文本见 openspec change
  `m8-docs-bilingual-restructure` 的 design §D6。
