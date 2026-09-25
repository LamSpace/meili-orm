# example-applications Specification

## Purpose

定义 examples 双代 demo 的覆盖面与可验证性：一套共享业务代码（全注解实体、透传 settings、回调、REST 场景端点）分别运行于 Boot 3.5.16 与 4.0.3 启动壳，配零外部依赖的装配冒烟测试与真机 curl 冒烟记录，作为用户视角的首份"可跑的文档"。

## Requirements

### Requirement: 共享业务代码单套字节码

examples SHALL 由 `meili-orm-example-common`（业务源码）与 boot3/boot4 两个仅含启动类、配置和各自 BOM 的薄壳模块组成：实体 `Book`（`Long id` 主键、`book_title` searchable order=1、`overview` searchable、嵌套 `Author{name,city}` 其中 city filterable、`tags` filterable、`genre` filterable、`price` sortable+filterable、`publishedAt` sortable、`@JsonIgnore` 排除字段）、`@MeiliSetting` 透传文件（rankingRules+stopwords）、`BeforeConvertCallback` 演示与 REST 控制器全部在 common，两 app 复用同一份 common 字节码。

#### Scenario: demo 装配冒烟零外部依赖

- **WHEN** 两 app 模块在 context-load 冒烟测试中启动（`meili.index.auto-init=none`、指向未监听端口）
- **THEN** 上下文加载成功：控制器、回调、serializer、operations bean 齐备，全程无需 Docker 与 MeiliSearch 服务

### Requirement: 场景端点全量面

demo SHALL 暴露以下 REST 端点并映射到 Operations API：`POST /api/books/import`（批量导入预置数据并返回导入条数，演示 wait-task 写后可查）、`GET /api/books/search`（q+genre+minPrice+sort+page+size→filter/sort/facet/分页全链路）、`GET /api/books/{id}`、`DELETE /api/books/{id}`、`GET /api/books/raw`（返回原始 JSON 逃生舱）。

#### Scenario: 真机场景脚本可跑通

- **WHEN** 按 demo README 启动 v1.49.0 服务并依次执行 curl 集
- **THEN** import 返回导入条数；中文 q+filter+sort+facet 检索命中且 Long id 原样无损；raw 端点返回服务端原始 JSON；delete 后按 id 查询为空

### Requirement: 启动行为可见

demo 应用配置 SHALL 使用 `wait-task=true`、`auto-init=sync-settings`、`on-settings-drift=warn`；首次启动日志 SHALL 出现索引创建与 settings 投影推送的 INFO 记录，供文档引用与手工核对。

#### Scenario: drift-WARN 手工观察留痕

- **WHEN** 给实体新增一个 filterable 字段后重启 demo（drift=warn）
- **THEN** 日志出现漂移 WARN 且不自动 apply；观察结论记录于冒烟留痕，实体还原

### Requirement: 真机冒烟留痕

M3 出口 SHALL 保存两 demo 各一轮真机冒烟记录（启动日志关键行、curl 命令与实际输出）于 demo README 与出口核对提交，记录内容 MUST 来自实际执行输出。

#### Scenario: 留痕可复核

- **WHEN** 查阅 demo README 冒烟小节
- **THEN** 其中含可复制执行的 docker/mvn/curl 命令序列与实际输出摘录，boot3 与 boot4 各一份
