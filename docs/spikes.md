# Spike 结论记录

M0 地基与风险清零的实证档案：Step 0 预检、M0.2 裸连通冒烟、spikeA、spikeB。
每节含验证内容 / 环境 / 结果 / 对设计的影响。执行变更：openspec `m0-foundation-risk-clearance`。

## Step 0 预检 —— SDK 0.21.0 签名实测（javap）

环境：`/home/lam/repo/com/meilisearch/sdk/meilisearch-java/0.21.0/meilisearch-java-0.21.0.jar`，JDK 25 `javap -cp`。

| 成员 | 实测签名 |
|---|---|
| Config 构造 | `Config(String)` / `Config(String,String)` / `Config(String,String,JsonHandler)` / `Config(String,String,JsonHandler,String[])`；另有 `setJsonHandler(JsonHandler)` |
| JsonHandler 接口 | `String encode(Object)`；`<T> T decode(Object, Class<T>, Class<?>...)`（均 throws MeilisearchException） |
| GsonJsonHandler / JacksonJsonHandler | 均 `implements JsonHandler`；`JacksonJsonHandler()` 与 `JacksonJsonHandler(ObjectMapper)` 两构造 |
| TaskInfo | `int getTaskUid()`；`TaskStatus getStatus()`；`String getIndexUid()`；`String getType()`；`Date getEnqueuedAt()` |
| Task | `int getUid()`；`TaskStatus getStatus()`；`String getType()`；`String getDuration()`；`TaskError getError()` |
| TaskStatus | 枚举 `ENQUEUED/PROCESSING/SUCCEEDED/FAILED/CANCELED` |
| Client | `TaskInfo createIndex(String)` / `createIndex(String,String)`；`TaskInfo deleteIndex(String)`；`Index index(String)`；`void waitForTask(int)`；`Task getTask(int)`；`Results<Task> getTasks(...)` |
| Index | `TaskInfo addDocuments(String)` / `updateDocuments(String)`（重载带 primaryKey/offset）；`String getRawDocument(String)`；`<T> T getDocument(String, Class<T>)`；`String rawSearch(String)` / `String rawSearch(SearchRequest)`；`Searchable search(SearchRequest)`；`Settings getSettings()`；`TaskInfo updateSettings(Settings)` 及各子设置三件套（含 granular `FilterableAttributesConfig[]`）；`void waitForTask(int)` / `waitForTask(int,int,int)`（uid, timeoutMs, intervalMs） |
| Settings | `set/getFilterableAttributes(String[])`、`set/getSearchableAttributes(String[])`、`set/getSortableAttributes(String[])`、`set/getDisplayedAttributes(String[])`、`set/getRankingRules(String[])`（fluent，返回 Settings） |
| SearchRequest | `public SearchRequest(String q)`；`String[] getFilter()`；`String[][] getFilterArray()`；`String[] getSort()` |

**对计划文本的修正（依计划"以 javap 实测为准"条款）**：
- ① `taskUid` 是 **`int`** 而非 `String`：spike/M1 网关一律 `int`（`awaitTask(int)`、`getTask(int)`）。设计文档 §5.3 的 `String createIndex(...)/awaitTask(String)` 签名在 M1 落地时须按此回改。
- ② raw 读通道 = `Index.getRawDocument(String):String`（及 `getRawDocuments()`），**不存在** `getDocument(String):String`；计划 Task 3 片段 `(String) index.getDocument(...)` 替换为 `getRawDocument(...)`。
- ③ `client.waitForTask(uid)` 返回 void（轮询至终态）；取终态对象用 `getTask(int)`。计划 Task 2 片段 `Task done = client.waitForTask(...)` 修正为先 wait 后 get。

## M0.2 裸连通冒烟（Docker v1.49.0 + SDK 直连建删索引）

环境：本机 Docker，`docker run -d --name meili-m0-smoke -p 7700:7700 -e MEILI_MASTER_KEY=masterKey-test-123456 -e MEILI_ENV=development getmeili/meilisearch:v1.49.0`（镜像本地已有，未拉取）；`curl /health` → `{"status":"available"}`。

冒烟程序：临时工程仅依赖 meilisearch-java 0.21.0，`Client.createIndex("m0_smoke","id")` → `waitForTask` → `getTask` → `deleteIndex` → `waitForTask`。实际输出：

```text
createIndex uid=0 status=enqueued
create final=succeeded
delete uid=1 final=succeeded
```

结论：**冒烟通过**。容器就绪、master key 鉴权、建/删索引、任务终态轮询全链可用。`TaskInfo.getStatus()` 的 `toString()` 输出为小写服务端字面量（`enqueued`/`succeeded`），断言一律用枚举常量比较（`isEqualTo(TaskStatus.SUCCEEDED)`），勿比较字符串。临时容器已清理。

### 本机 IT 硬前置补充（实测发现）

Docker CLI 走 Docker Desktop context（`~/.docker/desktop/docker.sock`），Testcontainers 亦可达；但 **Docker Hub 直连超时**（`registry-1.docker.io` context deadline exceeded），而 Testcontainers 需要 sidecar 镜像 `testcontainers/ryuk:0.12.0`（TC 1.21.4，本地原无 → 首跑容器启动以 `ContainerFetchException` 失败于 2 分钟拉取超时）。处置：经 `docker.m.daocloud.io/testcontainers/ryuk:0.12.0` 拉取并重打 tag 为本地 `testcontainers/ryuk:0.12.0`（镜像存在即不再拉取）。该前置为本机环境事实（与 maven.config 同性质），若换机需重做；另一可选路线是 `~/.testcontainers.properties` 置 `ryuk.disabled=true`（代价：容器回收靠手动）。

### 冒烟暴露的构建事实：okhttp-jvm 空壳问题（非计划假设）

`com.squareup.okhttp3:okhttp:5.3.2` 的 Maven jar 是 **0 类的多平台元数据空壳（767 字节）**，其 pom 不声明 `okhttp-jvm` 依赖（`published-with-gradle-metadata`，指望 Gradle 解析）；Maven 消费者运行时抛 `NoClassDefFoundError: okhttp3/MediaType`。首轮冒烟即因此失败，加入显式依赖 `com.squareup.okhttp3:okhttp-jvm:5.3.2` 后通过。

**处置（已落实到构建）**：根 pom `dependencyManagement` 同时钉 okhttp 与 okhttp-jvm 5.3.2；`meili-orm-core` 显式依赖 `okhttp-jvm`（compile/runtime 可见，保证经 starter 传递到用户 classpath）。设计文档 §2.3"OkHttp 5.3.2 api 作用域传递（连带 okio）"据此在 M0 收口回写中修正。

## spikeA 结论

**环境**：meilisearch-java 0.21.0 × 服务端 v1.49.0（Testcontainers 真容器）× JDK 25。哨兵：`meili-orm-core/src/test/.../spike/SpikeAJsonHandlerIT`（三臂，收紧后 3/3 绿）。

**观察与锁定**：

1. **对照组（默认 GsonJsonHandler）**：addDocuments→TaskInfo、getTask→Task、getSettings→Settings、getDocument→Map、getKeys→Results<Key> 全解析正常；`getDocument(id, Map.class)` 中 `9007199254740993` 解为 **`java.lang.Double`**（精度缺陷暴露，判权移交 spikeB）。
2. **实验组（JacksonJsonHandler）**：任务写读环节（addDocuments/waitForTask/getTask）**通过**；`updateSettings` **请求侧炸**——`Settings.encode` 把 Java 双视图字段 `filterableAttributesConfig` 一并序列化进 PATCH 体，服务端 400：`Unknown field \`filterableAttributesConfig\`（APIError.code=bad_request）`；纯读 `getSettings` 通过。
3. **探针组（计数委托 Gson）**：`decodeTargets = [TaskInfo, Task, Settings, Map, Results<Key>]`，`encodeCalls≥1`——**全部 typed 读环节经由可插拔 JsonHandler**，替换面即全读链。

**结论（写死，M2 装配依此执行）**：**「装配 Client 一律保持默认 GsonJsonHandler（`Config(url, key)` 二参构造）；实体读写主路径不依赖任何 JsonHandler，读路径唯一契约 = raw 字符串 API（`getRawDocument(String)` / `rawSearch(SearchRequest)` → 自有序列化器，spikeB 保证）」**。即使实验组部分环节通过，raw 通道仍是单一事实源——降低与 SDK 内部 Gson 注解（`@SerializedName`/双视图抑制）的隐式耦合；升级 SDK 时该哨兵变红即触发重新实证。

## spikeB 结论

**环境**：同 spikeA。哨兵：`meili-orm-core/src/test/.../spike/SpikeBRawJacksonPrecisionIT`（1/1 绿，收紧一次到位）。

**实测结果**（实体 `record Book(Long id, String title, long views, Author author, List<String> tags)`，主键/视图字段取 `9007199254740993` > 2^53）：

1. **坏通道事实锁定**：`index.getDocument(id, Map.class)`（Gson 内部 Map 通道）把 `id` 解为 `java.lang.Double` 且值失真（`longValue()=9007199254740992 ≠ 原值`）——断言以锁定失真形态常驻，"Long 主键精度问题"在 typed 便捷 API 上是必然，非偶发。
2. **主路径无损**：`index.getRawDocument("9007199254740993")` 返回原始 JSON 字符串 → 裸 Jackson `readValue(json, Book.class)`，`id`/`views` 逐位相等，中文（`三体`/`刘慈欣`/`北京`）、嵌套对象、字符串数组全部无损。
3. **搜索路径无损**：`index.rawSearch(new SearchRequest("三体"))` → `readTree(...).path("hits").get(0)` → `treeToValue(node, Book.class)`，同款逐位相等。

**结论（M1 实现契约，写死）**：实体读写唯一通道 = **raw JSON 字符串 ↔ 自有序列化器**；服务端数值经文本中转，Jackson 按目标类型解析，Long 精度问题在架构上消失（不经任何 `Number→Double` 环节）。

**M1 引用签名锚点**（javap + 实跑双确认）：

```text
Index.getRawDocument(String): String          Index.getRawDocuments(): String
Index.rawSearch(SearchRequest): String        Index.rawSearch(String): String
Index.addDocuments(String): TaskInfo          Index.updateDocuments(String): TaskInfo
TaskInfo.getTaskUid(): int（全链路 int，非 String）
Client.waitForTask(int) / Index.waitForTask(int,int,int): void（uid, timeoutMs, intervalMs）
Client.getTask(int): Task                    Task.getStatus(): TaskStatus（比较用枚举常量）
SearchRequest public 构造：SearchRequest(String q)；getFilter(): String[]；getSort(): String[]
```
