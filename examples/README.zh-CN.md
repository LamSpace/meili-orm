[English](README.md)

# meili-orm 演示工程

同一套业务代码（`meili-orm-example-common`）分别运行在 Boot 3.5.16 与 Boot 4.0.3 两个启动壳里，
演示注解映射、settings 投影同步、生命周期回调、raw 逃生舱与双代兼容。Starter 本体的文档见
[项目 README](../README.zh-CN.md)。

| 模块 | 角色 |
|---|---|
| `meili-orm-example-common` | 实体 `Book`/`Author`、`BeforeConvertCallback`、REST 控制器、预置数据（编译基线 Boot 3.5.16） |
| `meili-orm-example-boot3` | Boot 3.5.16 启动壳（仅启动类 + 配置 + 各代 BOM） |
| `meili-orm-example-boot4` | Boot 4.0.3 启动壳（复用 common 同一份字节码） |

演示配置（两个壳相同）：`wait-task=true`（写后可查）、`index.auto-init=sync-settings`、
`index.on-settings-drift=warn`（漂移只告警不动手）。

## 一键流程

```bash
# 1. 起服务端（钉版 v1.49.0）
docker run -d --name meili-demo -p 7700:7700 \
  -e MEILI_MASTER_KEY=demoMasterKey-0123456789 \
  -e MEILI_ENV=development getmeili/meilisearch:v1.49.0

# 2. 仓库根目录安装全部构件（含 examples；首次带测试全量构建需 Docker）
mvn -DskipTests install

# 3. 起演示应用（换 boot3 即 Boot 3.5.16 版，其余步骤完全相同）
mvn -pl examples/meili-orm-example-boot4 spring-boot:run
```

若你的 Maven 依赖自定义 settings 文件（镜像源、本地仓库布局），给上面两条 `mvn` 命令追加
`-s /path/to/your/settings.xml` 即可。

启动日志可见自动建索引与 settings 投影推送（实际输出摘录；投影规则见
[映射指南](../docs/zh-CN/mapping-guide.md)）：

```
INFO i.g.l.m.a.MeiliIndexInitializer : Index books created with projected settings synced (task 1)
INFO i.g.l.m.example.MeiliExampleApplication : Started MeiliExampleApplication in 1.611 seconds (process running for 1.841)
```

## curl 场景脚本与实测输出

以下输出为 2026-09-26 真机实测转录（boot4 壳；boot3 壳另跑一轮，行为实测一致——见文末冒烟记录）。

### 导入（批量、写后可查；书名带首尾空白的「 三体 」经回调规整为「三体」）

```bash
$ curl -s -X POST localhost:8080/api/books/import
{"imported":6}
```

### 检索（q + filter + sort + 分页 + facet 全链路；page 为 1 起步）

```bash
$ curl -s -G localhost:8080/api/books/search \
    --data-urlencode "q=三体" --data-urlencode "genre=科幻" \
    --data-urlencode "minPrice=30" --data-urlencode "sort=price:asc" \
    --data-urlencode "page=1" --data-urlencode "size=10"
{"hits":[{"id":9007199254740993,"title":"三体","overview":"文明存续的黑暗森林博弈，雨果奖最佳长篇小说。","author":{"name":"刘慈欣","city":"北京"},"tags":["科幻","雨果奖","硬科幻"],"genre":"科幻","price":59.0,"publishedAt":"2008-01-01T00:00:00Z"}],"estimatedTotalHits":null,"page":1,"hitsPerPage":10,"totalPages":1,"facetDistribution":{"genre":{"科幻":1}},"processingTimeMs":2}
```

### 按主键单读（Long 超 2^53 逐位无损）

```bash
$ curl -s localhost:8080/api/books/9007199254740993
{"id":9007199254740993,"title":"三体","overview":"文明存续的黑暗森林博弈，雨果奖最佳长篇小说。","author":{"name":"刘慈欣","city":"北京"},"tags":["科幻","雨果奖","硬科幻"],"genre":"科幻","price":59.0,"publishedAt":"2008-01-01T00:00:00Z"}
```

### raw 逃生舱（服务端原始 JSON 原样透传，可见投影后的文档字段名 `book_title`）

```bash
$ curl -s -G localhost:8080/api/books/raw --data-urlencode "q=活着"
{"hits":[{"id":2,"book_title":"活着","overview":"福贵一生的苦难与韧性，当代中国文学的基石之作。","author":{"name":"余华","city":"北京"},"tags":["现实主义","经典"],"genre":"文学","price":26.0,"publishedAt":"1993-08-01T00:00:00Z"}],"query":"活着","processingTimeMs":0,"limit":20,"offset":0,"estimatedTotalHits":1,"requestUid":"01a0ddf6-8c27-77e0-803a-919745a99768"}
```

### 删除

```bash
$ curl -s -o /dev/null -w "%{http_code}\n" -X DELETE localhost:8080/api/books/9007199254740993
204
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/books/9007199254740993
404
```

### 漂移观察（drift=warn 只告警不动手）

只要 `books` 索引的服务端 settings 与实体投影不一致——例如给实体临时增加一个 filterable 字段后
重启，或服务端设置被手工改动——启动日志即出现漂移告警且不动服务端：

```
WARN i.g.l.m.a.MeiliIndexInitializer : Index books settings drift with on-settings-drift=warn, no write, drifted keys [filterableAttributes]
```

此 WARN 行同样出自 2026-09-26 实测转录（以向服务端推送一个偏离投影的 `filterableAttributes` 值
制造漂移；随后复查 settings 确认服务端未被改写）。投影与服务端设置重新一致后，重启不再出现漂移
告警——warn 策略从不写入服务端。演示结束清理：`docker rm -f meili-demo`。

## 双代冒烟记录

- **boot4（Boot 4.0.3）**：上述全部命令与输出实测通过；Jackson3 模块未引入时序列化通道为默认
  Jackson2 自建实例（Boot 4 容器默认 `ObjectMapper` 是 Jackson 3，见
  [限制清单](../docs/zh-CN/limitations.md)第 4 条）。
- **boot3（Boot 3.5.16）**：同一份 common 字节码重跑 import/search/get/raw/delete 全绿
  （`{"imported":6}`、命中「活着」、GET 200、DELETE 204），无漂移告警；除 raw 输出中服务端签发的
  `requestUid` 外，转录与 boot4 一轮逐字节一致。
