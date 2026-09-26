# Proposal

## Why

设计文档预留"Testcontainers `@ServiceConnection` 可在 M4+ 接入"，M4 已闭环。`MeiliConnectionDetails` bean back-off 契约已存在（用户可手工桥接），`@ServiceConnection` 是让测试配置从"手写 bean"降为"一行注解"的语法糖，属 Boot 生态惯例预期。实证前提已变化：双代 SPI 坐标**不同名**——Boot 3.5.16 为 `org.springframework.boot.testcontainers.service.connection.ConnectionDetailsFactory`（独立 `spring-boot-testcontainers` 模块），Boot 4.0.3 为 `org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory`（并入 autoconfigure 模块、包名搬家，本地仓库无 4.0.3 testcontainers 构件）。单实现双兼容不可行，必须先 spike 定案桥接形态。

## What Changes

- 新增可选模块 `meili-orm-testcontainers`：类型化容器 `MeiliSearchContainer`（钉 `getmeili/meilisearch:v1.49.0`、7700 端口、master key 环境变量、`/health` 等待，镜像可覆盖）+ 把容器连接信息桥接为 `MeiliConnectionDetails` bean 的服务连接实现。
- 桥接实现形态由首个 spike 任务三选一定案：(a) boot3/boot4 双薄变体（镜像既有 commons 应急先例）、(b) 单模块双包实现 + 按代加载（前提是工厂发现机制对缺失接口类容错，需实测）、(c) 证伪降级——模块只保留类型化容器与手工桥接样例，`@ServiceConnection` 语法糖不进契约。
- starter 聚合面不变（新模块不被 `spring-boot-starter-meili-orm` 传递），文档新增 testcontainers 用法章节。

## Capabilities

### New Capabilities

- `testcontainers-service-connection`：类型化 MeiliSearch 容器定义、服务连接桥接语义（容器 → MeiliConnectionDetails）、与属性/用户 bean 的优先级 back-off。

### Modified Capabilities

（无——既有 spec 的行为不变；`@ServiceConnection` 桥接产物复用既有 `MeiliConnectionDetails` back-off 契约。）

## Impact

- **代码**：新模块 `meili-orm-testcontainers`（根 pom `<modules>` 追加）；`MeiliConnectionDetails` seam 加 1 行 `extends` Boot 的 `ConnectionDetails` 标记接口（服务连接工厂泛型界要求，行为零变化，boot3 桥接形态拍板）；core / starter / repository / jackson3 零改动。
- **测试**：模块自身 L2（桥接 bean 产出）+ L4 双代真机 IT（若 spike 走 (a) 则双变体各测；(c) 降级则单代容器 IT + 样例）。
- **构建/发布**：新模块属产品面（可发布坐标），Javadoc 与引用门禁生效；不引入 spring-data-commons。
- **网络**：Boot 4 侧 testcontainers 支持构件本地仓库缺席，首跑需联网拉取（同 it-boot3 首拉先例）。
- **文档**：README 功能表 + 新用法章节；`docs/` 相应登记。
