# meili-it-infrastructure Specification

## Purpose
提供 MeiliSearch 服务端集成测试的共享基建：钉版本的 Testcontainers 单例容器与 IT 基类，经 core test-jar 跨模块复用，并对 Docker 硬前置与裸连通冒烟留下可核对记录。
## Requirements
### Requirement: 钉版单例测试容器

系统 SHALL 提供共享的 MeiliSearch 容器定义：镜像固定 `getmeili/meilisearch:v1.49.0`、暴露 7700、设定 master key、以 `/health` HTTP 200 为就绪条件；同一 JVM 内所有 IT SHALL 复用同一容器实例。

#### Scenario: 容器就绪可服务

- **WHEN** 任一继承 IT 基类的测试执行到首个服务端调用
- **THEN** 容器已启动且 `/health` 返回 200，调用成功

#### Scenario: 镜像版本钉死不漂移

- **WHEN** 检查容器定义源码或其解析出的镜像坐标
- **THEN** 镜像 tag 恰为 `v1.49.0`，无 latest、无版本区间

### Requirement: IT 基类提供预配置客户端

系统 SHALL 提供集成测试基类，向子类暴露一个以容器 URL 与 master key 配置完成的 `com.meilisearch.sdk.Client`。

#### Scenario: 基类直连可用

- **WHEN** 子类通过基类客户端执行建索引→删除索引
- **THEN** 两步均成功且无需子类自行拼装连接参数

### Requirement: 基建经 test-jar 跨模块复用

`meili-orm-core` 构建 SHALL 产出 test-jar 制品，使后续模块（autoconfigure IT、it-boot3/4 矩阵）能以 `<type>test-jar</type>` 依赖复用容器与基类。

#### Scenario: test-jar 产出

- **WHEN** 执行 core 模块 `mvn package`
- **THEN** `target/` 下存在 `*-tests.jar`，其中含容器与基类 class

### Requirement: Docker 前置为声明式硬依赖

Docker 守护进程与本地 v1.49.0 镜像 SHALL 是集成测试的硬前提：前提缺失时 IT SHALL 快速失败且错误可辨识为容器环境问题，不得静默跳过。

#### Scenario: 缺 Docker 时失败形态可辨识

- **WHEN** Docker 守护进程不可用而运行 IT
- **THEN** 测试以容器/环境类异常失败，输出可定位到"Docker 前置缺失"

### Requirement: 裸连通冒烟记录

变更 SHALL 在 `docs/spikes.md` 留一份 M0.2 冒烟记录：本地 Docker 直接运行 v1.49.0，SDK 直连完成一次建索引与删索引，附命令与实际输出。

#### Scenario: 冒烟可复核

- **WHEN** 查阅 spikes.md 冒烟小节
- **THEN** 其中含可复制执行的命令序列与成功输出摘录

