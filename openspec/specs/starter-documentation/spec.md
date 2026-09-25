# starter-documentation Specification

## Purpose

定义首版发布候选的中文文档集契约：README、映射指南、限制清单、Boot3→Boot4 升级说明四份材料只转录已实现且已验证的行为，命令可复制执行，承诺不超出实现。

## Requirements

### Requirement: README 装配与功能面

根 `README.md` SHALL 含：starter Maven 坐标与最小装配（yml 三行级示例）、一个实体+搜索代码样例、功能表（对应设计文档 §1.1 目标集）、demo 一键运行命令转录、构建命令显式携带 `-s /home/lam/repo/settings.xml` 说明与 Docker/镜像前置声明；并链接其余三份指南。

#### Scenario: 命令零臆造

- **WHEN** 逐条核对 README 中出现的构建/docker/curl 命令
- **THEN** 每条均为本变更矩阵或 demo 真机执行过的原文转录

### Requirement: 限制清单完整且不超售

`docs/limitations.md` 与 README 限制小节 SHALL 逐项覆盖已实证限制并各给 workaround：连接/读超时不可配（SDK 硬约束）、`count` 走 stats 端点的语义、multiSearch v1 串行、Boot4 默认无 Jackson2 容器 mapper 时的自建行为、写后可查须 `wait-task`、改 filterable/sortable 触发全量重建、非目标表（无响应式、无 @Version、无 per-field analyzer、无 SpEL 动态索引名）；文档不得出现任何未实现承诺。

#### Scenario: 逐项对照核销

- **WHEN** 评审文档限制清单与实现/测试逐条对照
- **THEN** 每项限制有对应代码事实或哨兵测试依据，无超出实现范围的功能描述

### Requirement: 映射指南可独立教会注解

`docs/mapping-guide.md` SHALL 含：每个注解一行的"注解→MeiliSearch 概念/settings"对照表、"不标注=不声明"铁律及其后果（displayed 白名单效应）、嵌套点路径规则、`@MeiliSetting` 透传与投影的合并/覆盖优先级、`searchableOrder` 排序语义、生命周期回调四件套样例、settings 投影管线图。

#### Scenario: 对照表与实现一致

- **WHEN** 按指南对照表构造实体并核对投影输出（对照 golden 测试）
- **THEN** 指南描述与实际投影行为一致

### Requirement: 升级说明与依赖升级检查清单

`docs/boot3-to-boot4.md` SHALL 含：单代码库编译基线策略（autoconfigure 编译取 3.5.16、jackson3 模块为唯一取 Boot 4.0.3 BOM 的产品模块之例外声明）、双矩阵工作方式与 Docker 前置、jackson3 可选模块用法，以及**依赖升级检查清单**：变更任一钉版（meilisearch-java、两代 Boot、服务端 v1.49.0 镜像）后必须根 reactor 全绿（含双矩阵与哨兵）、重读 spikeA/spikeB 哨兵结论是否仍成立、并做一轮 demo 真机冒烟。

#### Scenario: 升级义务书面化

- **WHEN** 查阅升级说明的清单小节
- **THEN** 清单逐项给出可执行的验证命令与判定标准，覆盖 SDK/Boot/服务端三类钉版变更
