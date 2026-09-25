# meili-orm 演示工程

同一套业务代码（`meili-orm-example-common`）分别运行在 Boot 3.5.16 与 Boot 4.0.3 两个启动壳里，
演示注解映射、settings 投影同步、回调、raw 逃生舱与双代兼容。

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

# 2. 仓库根目录安装全部构件（含 examples；首次构建带测试需 Docker）
mvn -s /home/lam/repo/settings.xml -DskipTests install

# 3. 起演示应用（换 boot3 即 Boot 3.5.16 版，其余步骤完全相同）
mvn -s /home/lam/repo/settings.xml -pl examples/meili-orm-example-boot4 spring-boot:run
```

启动日志可见自动建索引与 settings 投影推送（实际输出摘录）：

```
INFO i.g.l.m.a.MeiliIndexInitializer : 索引 books 已创建并同步投影 settings（task 1）
INFO i.g.l.m.example.MeiliExampleApplication : Started MeiliExampleApplication in 1.728 seconds
```

## curl 场景脚本与实测输出

以下输出为 2026-09-25 真机实测转录（boot4 与 boot3 各跑一轮，行为一致）。

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
{"hits":[{"id":2,"book_title":"活着","overview":"福贵一生的苦难与韧性，当代中国文学的基石之作。","author":{"name":"余华","city":"北京"},"tags":["现实主义","经典"],"genre":"文学","price":26.0,"publishedAt":"1993-08-01T00:00:00Z"}],"query":"活着","processingTimeMs":0,"limit":20,"offset":0,"estimatedTotalHits":1,"requestUid":"01a0d8dc-5120-7d73-b707-7fc75b4525b5"}
```

### 删除

```bash
$ curl -s -o /dev/null -w "%{http_code}\n" -X DELETE localhost:8080/api/books/9007199254740993
204
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/books/9007199254740993
404
```

### 漂移观察（drift=warn 只告警不写入）

给实体临时增加一个 filterable 字段后重启应用，日志出现漂移告警且 settings 未被改动：

```
WARN i.g.l.m.a.MeiliIndexInitializer : 索引 books settings 漂移，on-settings-drift=warn 不写入，漂移键 [filterableAttributes]
```

还原实体并重建后再次启动（Boot 3.5.16 壳，banner `(v3.5.16)`）：日志无漂移告警——印证 warn
策略从不写入服务端，还原后投影与服务端重新一致。演示结束清理：`docker rm -f meili-demo`。

## 双代冒烟记录

- **boot4（Boot 4.0.3）**：上述全部命令与输出实测通过；Jackson3 未引入时序列化通道为默认
  Jackson2 自建实例（Boot 4 容器默认 ObjectMapper 是 Jackson 3，见 `docs/limitations.md`）。
- **boot3（Boot 3.5.16）**：同一份 common 字节码重跑 import/search/get/delete 全绿
  （`{"imported":6}`、命中「活着」、GET 200、DELETE 204），无漂移告警。
