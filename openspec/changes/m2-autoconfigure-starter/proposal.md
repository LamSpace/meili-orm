# Proposal

## Why

M1 已交付零 Spring 的 `meili-orm-core`（映射元模型、raw 读写通道、Operations、settings 投影、回调），但用户仍须手工组装 `Client`、序列化器、网关与回调链——距离"加依赖 + 3 行配置即用"的 Spring Boot Starter 语义只差自动配置层。M2 补齐这一层，使 `spring-boot-starter-meili-orm` 达到"Boot 4.0.3 示例应用加依赖即用"的里程碑出口。

## What Changes

- `meili-orm-spring-boot-autoconfigure` 模块从占位 pom 变为实体模块：新增两级自动配置（客户端层、数据层）与独立的索引初始化自动配置，共 3 个 `@AutoConfiguration` 类，经 `META-INF/spring/...AutoConfiguration.imports` 注册。
- 新增配置属性集 `meili.*`（`MeiliProperties`）：enabled、url、api-key、wait-task、wait-timeout、index.auto-init、index.on-settings-drift，附 IDE 提示元数据（`additional-spring-configuration-metadata.json`）。
- 新增连接抽象 `MeiliConnectionDetails`（可被用户 bean / 未来 Testcontainers `@ServiceConnection` 整体替换）与扩展 SPI `MeiliConfigCustomizer`。
- 新增 `MeiliIndexInitializer`：启动期扫描 `@MeiliDocument` 实体，按 auto-init 三模式建索引/同步 settings，漂移按 warn/apply/fail 三策略处置；**服务端不可达时 fail-fast，应用启动失败**（与 drift=fail 同一启动期严格语义，本 change 确立的契约）。
- 所有装配 bean 挂 `@ConditionalOnMissingBean` back-off；`MeiliDocumentSerializer` 优先采用容器 `ObjectMapper`（Boot 3 常态），缺失时自建稳定默认实例。
- 新增装配线哨兵测试：借 `MeiliConfigCustomizer` 捕获构建前 `Config`，断言 `hostUrl`/`apiKey` 透传且 `jsonHandler` 保持默认 `GsonJsonHandler`（防止未来改动重蹈已实证的 typed-API 不兼容）。
- `spring-boot-starter-meili-orm` 聚合 pom 补全 `<description>` 与依赖核对；新增 imports/元数据完整性断言测试。
- M2 出口：全 reactor `mvn -s /home/lam/repo/settings.xml clean verify` 绿 + `/tmp/smoke` 最小 Boot 4.0.3 应用引 starter、3 行配置、真机（Docker v1.49.0）冒烟通过并留记录。
- **core 模块零改动**（M1 测试套件原样充当回归防线）。

## Capabilities

### New Capabilities

- `autoconfigure-client`：`meili.*` 配置属性、`MeiliConnectionDetails` 抽象、`MeiliConfigCustomizer` SPI、SDK `Client` bean 的条件链与 back-off 语义、`meili.enabled` 总开关。
- `autoconfigure-data`：数据层 bean 装配（serializer/mapping context/entity callbacks/raw gateway/operations）、容器 `ObjectMapper` 择优、`MeiliCallback` bean 收集、`@MeiliDocument` 类路径扫描器。
- `index-auto-initialization`：启动期索引初始化生命周期——auto-init 三模式、settings diff 语义、drift 三策略（含 filterable/sortable 全量重建代价告警）、重复索引名 fail-fast、服务端不可达 fail-fast。
- `starter-packaging`：starter 聚合坐标、`AutoConfiguration.imports` 注册完整性、配置属性元数据在册（IDE 提示）。

### Modified Capabilities

（无。`module-build-foundation` 与 `meili-it-infrastructure` 的既有需求被 M2 复用而非修改；`meili-orm-core` 公开契约不变。）

## Impact

- **代码**：`meili-orm-spring-boot-autoconfigure/src/main|test`（全部新增类与 L2 测试套件）、`spring-boot-starter-meili-orm/pom.xml`（description 补全）；`meili-orm-core` 不改动。
- **构建**：autoconfigure pom 新增 `spring-boot-configuration-processor`（optional，本地仓库缺 3.5.16 需联网拉取）与 `spring-boot-starter-test`（test 作用域，3.5.16 同样需联网）；首个主源码类落地时移除该模块 javadoc `skip=true` 临时豁免（聚合层门禁即刻生效）。
- **门禁**：src/main 注释受内部引用扫描约束（不得引用 spike 记录/设计文档节号等内部材料，须只读自洽）；Javadoc show=private 全量适用。
- **测试运行前置**：Task 14 真机 IT 与出口冒烟需 Docker 守护进程与本地 `getmeili/meilisearch:v1.49.0` 镜像（当前 Docker 未运行，属开工前人工准备项）。
- **兼容性风险窗口**：autoconfigure 以 Boot 3.5.16 编译，Boot 4 运行时验证归 M3 双矩阵；M2 出口冒烟用 Boot 4.0.3 真实运行，提前探一次装配面。
