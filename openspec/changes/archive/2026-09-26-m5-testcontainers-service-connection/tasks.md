# Tasks

## 1. SPI spike 定案（阻塞后续任务组）

- [x] 1.1 定位 Boot 4.0.x 服务连接支持面：构件坐标、`@ServiceConnection` 注解与工厂 SPI 的 FQCN、工厂发现机制与时序；本地仓库无 4.0.x testcontainers 构件时联网首拉（失败=停下报告，不改钉版）。验证：结论以 FQCN/构件名清单形式回写本变更 design.md D1，并明确"4.x 支持面：具备/未备"。
- [x] 1.2 PoC 实测工厂发现对"引用缺失接口的工厂条目"是否容错（构造双代各一个假工厂条目跑上下文，观察 NoClassDefFoundError 行为）。验证：三选一判定（(b) 单模块双包 / (a) 双薄变体 / (c) 降级）连同 PoC 证据回写 design.md D1；若判 (c)，同步删除本 delta specs 的"服务连接桥接"requirement 与 proposal 对应条目并继续任务组 2（仅容器部分）。

## 2. 模块实现

- [x] 2.1 按 D1 定案建 `meili-orm-testcontainers` 模块骨架（根 pom `<modules>` 追加、pom `<description>` 合规、package-info、Javadoc/引用门禁全量生效、参与发布面无 deploy skip）。验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-testcontainers -am verify` 绿。（注：纯骨架期 javadoc 门禁报 "No public or protected classes"，门禁收编进 2.2/2.3 的带类验证，同令全绿；骨架仅 package-info 的独立验证不成立，为 javadoc 机制使然而非缺陷。）
- [x] 2.2 `MeiliSearchContainer`（默认镜像 v1.49.0、端口 7700、master key env、`/health` 等待、镜像/密钥可覆盖、`getUrl()`/`getApiKey()`）+ L1 配置断言 + Docker 真机"默认容器健康就绪"IT。验证：L1 绿；真机 IT 两场景（就绪+覆盖）绿。
- [x] 2.3 服务连接桥接（按定案形态）：桥接仅产 `MeiliConnectionDetails`，用户自有 bean 时退避。验证：真机 IT——@ServiceConnection 声明容器启动上下文，保存后按主键读取往返成功且构建前 Config 观测到容器 URL；另一上下文声明用户 ConnectionDetails bean，断言桥接退避生效。判 (c) 时本任务改为 README 手工桥接样例并跑通样例代码本身。
- [x] 2.4 文档：README 功能表加 testcontainers 行 + 新章节 `docs/testcontainers.md`（一行注解用法 + 手工桥接样例 + 双代支持矩阵按 D1 定案如实标注）。验证：文档内命令/代码片段照跑通过。

## 3. 集成验证

- [x] 3.1 出口核对：全 reactor `mvn -s /home/lam/repo/settings.xml -q clean verify` 绿（含新模块与双矩阵）；`mvn dependency:tree -pl spring-boot-starter-meili-orm` 不含新模块与 `org.testcontainers`；`bash scripts/check-source-citations.sh` 零退出；`openspec validate m5-testcontainers-service-connection --type change` 通过。验证：四条命令输出留痕于提交说明。
