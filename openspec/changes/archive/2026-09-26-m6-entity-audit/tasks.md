# Tasks

## 1. 注解与元模型

- [x] 1.1 写 L1 失败测试：`@CreatedDate`/`@LastModifiedDate` 解析标记（含许可集六类型）、`String` 审计字段解析抛 `MeiliMappingException` 且消息含类名字段名、审计字段与 `@MeiliField` 角色标注共现不互相干扰、无审计实体解析回归不变。验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-core test` 因新用例红。
- [x] 1.2 实现两注解（core mapping 包，Javadoc 齐备）+ `MeiliPersistentProperty` 审计标记 + `MeiliPersistentEntity` 解析与类型校验。验证：1.1 转绿且 core 全量 test 绿。

## 2. 填充逻辑

- [x] 2.1 写 L1 失败测试（mock 网关，复用既有 operations 测试基建）：全新实体 created/modified 均填（before/after 时间窗）；`long` 的 0 哨兵填充与用户非零值保留；重载再保存 created 不变 modified 进新窗；POJO 返回同一实例、record 返回新实例且非审计组件逐项相等、无审计实体返回同一实例；`BeforeConvertCallback` 收到已填充实体；`saveAll` 逐实体独立填充；`findById`/`deleteById` 不触碰审计字段。验证：运行红。
- [x] 2.2 实现 `MeiliAuditSupport`（internal：时间源锚 `Instant.now()` 按类型换算、POJO 反射写回、record 规范构造器重建）并在 `DefaultMeiliSearchOperations` save/saveAll 于回调前接线。验证：2.1 全绿且 core 全量 test 绿。

## 3. 真机与文档

- [x] 3.1 L3 真机 IT（`wait-task=true`，Testcontainers v1.49.0）：save→GET 服务端文档含双时间戳且 created 非空→findById 回读 created/modified 与保存值一致（Long 精度链路复用）→再 save→回读 created 同值、modified 落于新窗。验证：`mvn -s /home/lam/repo/settings.xml -q -pl meili-orm-core verify` 绿。
- [x] 3.2 文档：`docs/mapping-guide.md` 新增审计章节（两注解语义、六类型许可集、0 哨兵、record 重建与 compact constructor 幂等要求、与回调顺序）；README 功能表加审计行；`docs/limitations.md` 补两条（created 为空值填充不判定服务端存在性；无 createdBy 类语义）。验证：三份文档与 specs 语义逐条对照一致、通读自洽。

## 4. 集成验证

- [x] 4.1 出口核对：全 reactor `mvn -s /home/lam/repo/settings.xml -q clean verify` 绿、`bash scripts/check-source-citations.sh --selftest` 与本体零退出、`openspec validate m6-entity-audit --type change` 通过、SDK 类型零泄漏核查（新增公开 API 签名无 `com.meilisearch.*`）。验证：四条命令/检查输出留痕于提交说明。
