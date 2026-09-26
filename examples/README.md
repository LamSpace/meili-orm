[中文](README.zh-CN.md)

# Meili-ORM demo projects

The same business code (`meili-orm-example-common`) runs in two startup shells — Boot 3.5.16 and
Boot 4.0.3 — demonstrating annotation mapping, settings projection sync, lifecycle callbacks, the
raw escape hatch, and dual-generation compatibility. The starter itself is documented in the
[project README](../README.md).

| Module | Role |
|---|---|
| `meili-orm-example-common` | `Book`/`Author` entities, `BeforeConvertCallback`, REST controller, preset data (compiled at the Boot 3.5.16 baseline) |
| `meili-orm-example-boot3` | Boot 3.5.16 startup shell (main class + config + per-generation BOM only) |
| `meili-orm-example-boot4` | Boot 4.0.3 startup shell (reuses the identical common bytecode) |

Demo configuration (identical in both shells): `wait-task=true` (read-after-write),
`index.auto-init=sync-settings`, `index.on-settings-drift=warn` (drift warns only, never writes).

## One-command flow

```bash
# 1. Start the server (pinned to v1.49.0)
docker run -d --name meili-demo -p 7700:7700 \
  -e MEILI_MASTER_KEY=demoMasterKey-0123456789 \
  -e MEILI_ENV=development getmeili/meilisearch:v1.49.0

# 2. From the repository root, install all artifacts (includes the examples; first full
#    build with tests requires Docker)
mvn -DskipTests install

# 3. Run the demo app (swap in boot3 for the Boot 3.5.16 shell; every other step is identical)
mvn -pl examples/meili-orm-example-boot4 spring-boot:run
```

If your Maven installation relies on a custom settings file (mirrors, local repository layout),
pass it to both `mvn` commands above with `-s /path/to/your/settings.xml`.

The startup log shows the index auto-creation and the settings projection push (actual output
excerpt; the projection rules are in the [mapping guide](../docs/mapping-guide.md)):

```
INFO i.g.l.m.a.MeiliIndexInitializer : Index books created with projected settings synced (task 1)
INFO i.g.l.m.example.MeiliExampleApplication : Started MeiliExampleApplication in 1.611 seconds (process running for 1.841)
```

## curl scenarios with live output

The output below is a live transcript from 2026-09-26 (Boot 4.0.3 shell; the Boot 3.5.16 shell
was verified behaviorally identical in a separate live run — see the smoke record at the end).

### Import (bulk, read-after-write; the title ` 三体 ` with surrounding whitespace is normalized to `三体` by the callback)

```bash
$ curl -s -X POST localhost:8080/api/books/import
{"imported":6}
```

### Search (full q + filter + sort + pagination + facet chain; page is 1-based)

```bash
$ curl -s -G localhost:8080/api/books/search \
    --data-urlencode "q=三体" --data-urlencode "genre=科幻" \
    --data-urlencode "minPrice=30" --data-urlencode "sort=price:asc" \
    --data-urlencode "page=1" --data-urlencode "size=10"
{"hits":[{"id":9007199254740993,"title":"三体","overview":"文明存续的黑暗森林博弈，雨果奖最佳长篇小说。","author":{"name":"刘慈欣","city":"北京"},"tags":["科幻","雨果奖","硬科幻"],"genre":"科幻","price":59.0,"publishedAt":"2008-01-01T00:00:00Z"}],"estimatedTotalHits":null,"page":1,"hitsPerPage":10,"totalPages":1,"facetDistribution":{"genre":{"科幻":1}},"processingTimeMs":2}
```

### Single read by primary key (Long beyond 2^53 stays bit-exact)

```bash
$ curl -s localhost:8080/api/books/9007199254740993
{"id":9007199254740993,"title":"三体","overview":"文明存续的黑暗森林博弈，雨果奖最佳长篇小说。","author":{"name":"刘慈欣","city":"北京"},"tags":["科幻","雨果奖","硬科幻"],"genre":"科幻","price":59.0,"publishedAt":"2008-01-01T00:00:00Z"}
```

### Raw escape hatch (the server's raw JSON passes through verbatim; note the projected document field name `book_title`)

```bash
$ curl -s -G localhost:8080/api/books/raw --data-urlencode "q=活着"
{"hits":[{"id":2,"book_title":"活着","overview":"福贵一生的苦难与韧性，当代中国文学的基石之作。","author":{"name":"余华","city":"北京"},"tags":["现实主义","经典"],"genre":"文学","price":26.0,"publishedAt":"1993-08-01T00:00:00Z"}],"query":"活着","processingTimeMs":0,"limit":20,"offset":0,"estimatedTotalHits":1,"requestUid":"01a0ddf6-8c27-77e0-803a-919745a99768"}
```

### Delete

```bash
$ curl -s -o /dev/null -w "%{http_code}\n" -X DELETE localhost:8080/api/books/9007199254740993
204
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/books/9007199254740993
404
```

### Drift observation (drift=warn warns only, never writes)

Whenever the server-side settings of the `books` index no longer match the entity projection —
for example after adding a filterable field to the entity, or after any manual change to the
server settings — a restart logs the drift and leaves the server untouched:

```
WARN i.g.l.m.a.MeiliIndexInitializer : Index books settings drift with on-settings-drift=warn, no write, drifted keys [filterableAttributes]
```

This WARN line is itself transcribed from the 2026-09-26 run (drift induced by pushing a
`filterableAttributes` value that deviates from the projection; a follow-up settings fetch
confirmed the server had not been rewritten). Once the projection and the server settings are
back in sync, restarts log no drift warning — the warn strategy never writes to the server.
After the demo, clean up with `docker rm -f meili-demo`.

## Dual-generation smoke record

- **boot4 (Boot 4.0.3)**: all the commands and outputs above pass live. With the Jackson 3 module
  not on the classpath, the serialization channel is the default self-built Jackson 2 instance
  (the Boot 4 container's default `ObjectMapper` is Jackson 3 — see item 4 of the
  [limitations list](../docs/limitations.md)).
- **boot3 (Boot 3.5.16)**: the same common bytecode re-ran import/search/get/raw/delete all green
  (`{"imported":6}`, hit on `活着`, GET 200, DELETE 204), no drift warning; the transcript is
  byte-identical to the boot4 run except for the server-assigned `requestUid` in the raw output.
