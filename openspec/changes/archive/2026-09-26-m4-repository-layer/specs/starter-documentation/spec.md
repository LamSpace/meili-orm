# Spec Delta

## MODIFIED Requirements

### Requirement: README 装配与功能面

根 `README.md` SHALL 含：starter Maven 坐标与最小装配（yml 三行级示例）、一个实体+搜索代码样例、功能表（对应设计基线目标集，Repository 层作为独立坐标可选能力在功能表与装配小节中如实标注——含"repository 不在 starter 聚合内、需显式加 `meili-orm-repository` 依赖"的一句话说明）、一个仓库接口+派生查询代码样例、demo 一键运行命令转录、构建命令显式携带 `-s /home/lam/repo/settings.xml` 说明与 Docker/镜像前置声明；并链接其余三份指南。

#### Scenario: 命令零臆造

- **WHEN** 逐条核对 README 中出现的构建/docker/curl 命令
- **THEN** 每条均为本变更矩阵或 demo 真机执行过的原文转录

#### Scenario: Repository 装配面准确

- **WHEN** 按 README 的 repository 小节从零搭建最小工程
- **THEN** 仅按文档声明的坐标与配置即可获得可注入的 `MeiliRepository`，无需查阅其他材料

### Requirement: 限制清单完整且不超售

`docs/limitations.md` 与 README 限制小节 SHALL 逐项覆盖已实证限制并各给 workaround：连接/读超时不可配（SDK 硬约束）、`count` 走 stats 端点的语义、multiSearch v1 串行、Boot4 默认无 Jackson2 容器 mapper 时的自建行为、写后可查须 `wait-task`、改 filterable/sortable 触发全量重建、非目标表（无响应式、无 @Version、无 per-field analyzer、无 SpEL 动态索引名）；Repository 面 SHALL 追加：`Page.getTotalElements()` 为估算值、`findAll()` 受 maxTotalHits 截断、Containing/Like 为全文近似（非子串语义）、`deleteAll(Iterable)` 逐条请求、派生查询关键字支持子集与启动期角色预检（透传声明不进入预检输入）、无 DTO 投影/Stream/异步返回形态；文档不得出现任何未实现承诺。

#### Scenario: 逐项对照核销

- **WHEN** 评审文档限制清单与实现/测试逐条对照
- **THEN** 每项限制有对应代码事实或哨兵测试依据，无超出实现范围的功能描述

### Requirement: 映射指南可独立教会注解

`docs/mapping-guide.md` SHALL 含：每个注解一行的"注解→MeiliSearch 概念/settings"对照表、"不标注=不声明"铁律及其后果（displayed 白名单效应）、嵌套点路径规则、`@MeiliSetting` 透传与投影的合并/覆盖优先级、`searchableOrder` 排序语义、生命周期回调四件套样例、settings 投影管线图；并新增派生查询章：方法名关键字→MeiliQuery 行为对照表（支持/不支持两个清单，与实现报错面逐字一致）、投影名桥接规则（`@MeiliField.name` 与点路径如何出现在生成的 filter 中）、`@MeiliQuery` 模板语法与参数绑定/转义规则、角色预检的修复指引示例。

#### Scenario: 对照表与实现一致

- **WHEN** 按指南对照表构造实体并核对投影输出（对照 golden 测试）
- **THEN** 指南描述与实际投影行为一致

#### Scenario: 派生查询表可复算

- **WHEN** 按派生查询章的任一对照行写方法名并运行 L1 翻译断言
- **THEN** 生成的 filter/q/sort 与指南给出的目标串一致；列入"不支持"清单的方法名确实启动报错

### Requirement: 升级说明与依赖升级检查清单

`docs/boot3-to-boot4.md` SHALL 含：单代码库编译基线策略（autoconfigure 编译取 3.5.16、repository 编译取 commons 3.5.13、jackson3 模块为唯一取 Boot 4.0.3 BOM 的产品模块之例外声明）、双矩阵工作方式与 Docker 前置、jackson3 可选模块用法、repository 模块的 commons 两代运行期兼容面说明，以及**依赖升级检查清单**：变更任一钉版（meilisearch-java、两代 Boot、spring-data-commons、服务端 v1.49.0 镜像）后必须根 reactor 全绿（含双矩阵与哨兵）、重读 spikeA/spikeB 哨兵结论是否仍成立、并做一轮 demo 真机冒烟。

#### Scenario: 升级义务书面化

- **WHEN** 查阅升级说明的清单小节
- **THEN** 清单逐项给出可执行的验证命令与判定标准，覆盖 SDK/Boot/commons/服务端四类钉版变更
