# Proposal

## Why

M0–M2 已交付功能面（core 全链路 + 两级自动配置 + starter），但"单代码库同兼容 Spring Boot 3.5.x 与 4.x"这一架构决策（方案 A）至今只在 Boot 4.0.3 侧真机验证过；同时对外文档与用户视角示例完全缺失，发布候选无从定义。M3 把兼容承诺固化为常驻矩阵、给出可跑的 demo 与可信的文档，使首版发布进入可评审状态。

## What Changes

- 新增 `it/meili-orm-it-boot3`（钉 Boot 3.5.16）与 `it/meili-orm-it-boot4`（钉 Boot 4.0.3）兼容矩阵模块：同一 starter 装配 IT 在两代各跑一轮，含 Boot 版本哨兵断言防"矩阵没钉住版本"的假绿；双份同名源码复制而非 test-jar 共享（两代字节码隔离）。
- 新增可选模块 `meili-orm-serializer-jackson3`：自带 `AutoConfiguration.imports`，`@ConditionalOnClass(tools.jackson…)` 条件下"加依赖即接管" `MeiliDocumentSerializer`，排序先于 `MeiliDataAutoConfiguration`；序列化行为与 Jackson2 实现逐项镜像（改名/Long 精度/ISO 日期/未知键忽略）。
- 新增 `examples/`：common 业务代码（全注解 `Book` 实体 + `@MeiliSetting` 透传 + `BeforeConvertCallback` + REST 端点）+ boot3/boot4 两个启动壳，共享同一套字节码；每个 app 模块含零 Docker 依赖的 context-load 冒烟测试（`auto-init=none`，进 `mvn verify`）；真机 curl 冒烟记录进 demo README 与出口核对。
- 新增中文文档四件套：根 `README.md`（坐标、最小装配、功能表、限制清单、构建命令）、`docs/mapping-guide.md`（注解→settings 对照、投影管线）、`docs/limitations.md`、`docs/boot3-to-boot4.md`（含依赖升级检查清单：换 SDK/Boot/服务端钉版后必须全矩阵绿 + spike 哨兵结论重读）；文档只转录已实际验证的行为与命令。
- 根 pom `<modules>` 扩为含 `it`、`meili-orm-serializer-jackson3`、`examples`；it/examples 不进发布物（不参与 deploy 语义）。
- 无 **BREAKING** 变更：全部为新增模块与文档，既有公开 API 签名不动。

## Capabilities

### New Capabilities

- `dual-boot-compatibility-matrix`：Boot 3.5.16 / 4.0.3 双代编译运行矩阵、版本哨兵、starter 装配+CRUD 往返等价性，方案 A 的常驻护栏。
- `jackson3-serializer`：可选 Jackson 3 序列化接管模块的依赖形态、条件装配、行为镜像与回退语义。
- `example-applications`：双代 demo 的实体/端点覆盖面、common 共享字节码、context-load 冒烟与真机冒烟记录。
- `starter-documentation`：README/映射指南/限制清单/升级说明的内容契约（只转录已验证行为、命令可复制执行、升级检查清单）。

### Modified Capabilities

- `module-build-foundation`：多模块聚合结构要求从"三模块"扩为含 it/序列化可选模块/examples 的完整 reactor，并新增"验证与示例模块不产出发布物"的构建纪律。

## Impact

- 构建：根 pom 模块清单、新增 5 个 pom（it 聚合、it×2、jackson3、examples 聚合 + common + boot3/boot4 app）；`mvn clean verify` 覆盖面扩大（仍要求 Docker，与既有 IT 一致）。
- 依赖：jackson3 模块是唯一编译基线取 Boot 4.0.3 BOM（为拿 `tools.jackson` 坐标）的产品模块，该例外须写入升级文档；examples 依赖 starter + `spring-boot-starter-web`。
- 门禁：全部新增 Java 源受 Javadoc（show=private）与 `check-source-citations.sh` 约束。
- 代码：既有 core/autoconfigure/starter 零改动（预期）；若矩阵暴露装配问题，按"停下汇报、不私改架构"纪律处理。
- 文档：新增根 README 与 docs/ 三份指南；docs/spikes.md、demo README 追加冒烟记录。
