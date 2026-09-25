# meili-orm 映射指南

一句话分工：**文档格式转换全权交给 Jackson；元模型只管"索引名 / 主键 / 字段角色"三件事**。
MeiliSearch 没有 per-field mapping 概念，所以"映射"的落地形态是 **settings 投影**——
注解声明的字段角色在启动期投影为服务端 settings 数组。

## 注解对照表

| 注解 | 对应 MeiliSearch 概念 | 要点 |
|---|---|---|
| `@MeiliDocument(indexName)` | 索引 uid | v1 仅静态名；多实体声明同一 indexName 启动即失败 |
| `@MeiliId` | 主键属性 | **必须且只能一个**；类型仅 String 或整型（`int/long/Integer/Long`）；命名不必叫 `id`，天然规避服务端 `xxxId` 自动推断的多候选坑 |
| `@MeiliField(name)` | 文档字段名 | 序列化/反序列化/投影三方共用一名；与 `@JsonProperty` 同时声明且不一致 → 启动失败（冲突裁决优先，优先级：`@MeiliField.name` > `@JsonProperty` > Java 名） |
| `@MeiliField(searchable=true, searchableOrder=n)` | `searchableAttributes` | 数组顺序即搜索权重（见下） |
| `@MeiliField(filterable=true)` | `filterableAttributes` | 等值过滤语义 |
| `@MeiliField(sortable=true)` | `sortableAttributes` | 排序声明 |
| `@MeiliField(displayed=true)` | `displayedAttributes` | **白名单效应**：只要有任何字段声明 displayed，投影数组即只含声明字段——未声明字段将不出现在搜索命中里（主键不受限）。谨慎使用 |
| `@MeiliSetting(settingPath)` | 完整 settings 透传 | 类级、可重复、`classpath:` 前缀（见"透传规则"） |
| Jackson `@JsonIgnore` | 字段排除 | 不入库、不参与投影；不另造 `@MeiliTransient` |

## 语义铁律：不标注 = 不声明

某角色数组**只有当至少一个非主键字段声明了它**才会生成；无人声明则该键在投影 JSON 中
缺席，settings 同步绝不覆盖服务端对应默认值。全部实体都只声明用到的角色。

## searchableOrder 排序规则

投影数组的装配顺序：

1. 声明了 `searchableOrder >= 0` 的字段按其值升序排在前；
2. 其余 searchable 字段（`order = -1`，默认）按点路径字典序排在后。

同一实体内显式 order 值重复会在启动期抛出 `MeiliMappingException`。
`filterableAttributes` / `sortableAttributes` / `displayedAttributes` 恒按点路径字典序。

## 嵌套对象与点路径

非简单类型（POJO/record）属性在投影时**展平为点路径**叶子：`@MeiliField(filterable=true)`
标注在 `Author.city` 上投影为 `"author.city"`，与 MeiliSearch 对嵌套文档的展平检索语义
对齐。规则：

- 简单类型（String/数字/日期/枚举/数组/Map 等）为叶子，可直接声明角色；
- 展开深度上限 3，自引用以 visited 集合截断；
- 集合/Map 整体为不透明叶子，不在其上继续展开；
- 能展开出子字段的聚合字段自身不得声明角色。

## @MeiliSetting 透传规则

- 载体是**类级可重复注解**（刻意不放进 `@MeiliDocument`，与字段角色职责分离）；
- `settingPath` 指向类路径 JSON 对象文件；文件缺失、非对象、含未知键都在启动期失败，
  错误消息携带文件名与具体键；
- 允许键白名单（19 项）：`searchableAttributes`、`filterableAttributes`、
  `sortableAttributes`、`displayedAttributes`、`rankingRules`、`synonyms`、`stopWords`、
  `distinctAttribute`、`typoTolerance`、`faceting`、`pagination`、`searchCutoffMs`、
  `dictionary`、`separatorTokens`、`nonSeparatorTokens`、`proximityPrecision`、
  `embedders`、`localizedAttributes`；
- 多个 `@MeiliSetting` 按声明顺序合并，后声明文件覆盖先声明；
- **透传优先**：透传文件声明了四个角色数组中的任何一个时，该数组以透传值为准、覆盖
  注解投影（其余键两路合并）；
- 透传键与角色注解显式矛盾（同一数组既投影又透传且值不同）不报错——按"透传优先"处置，
  这是有意的逃生舱语义。

## 投影管线

```
 @MeiliDocument + 字段角色注解 + @MeiliSetting 透传文件
        |
        v
 MeiliSettingsProjection（纯函数，启动期执行，运行期零开销）
   -> searchableAttributes(按 order)/filterableAttributes/
      sortableAttributes/displayedAttributes  ⊕ 透传 JSON（透传优先）
        |
        v   （auto-init != none 且投影非空时）
 与 GET /indexes/{uid}/settings 实际值 diff
   （只比对投影声明过的键：数组按集合等值、searchableAttributes 按序等值；
     未声明键永不参与比较、永不被写入）
        |
        +-- 无漂移 ------------------> DEBUG 日志，no-op
        +-- 有漂移 ------------------> 按模式与策略处置（下表）
```

## auto-init × on-settings-drift 行为矩阵

| | 索引不存在 | 索引存在、无漂移 | 索引存在、有漂移 |
|---|---|---|---|
| `auto-init=none` | 不交互 | 不交互 | 不交互 |
| `create-if-missing`（默认） | 建索引 + 推送投影 settings + 等待任务 | DEBUG | **只报告不写入**（warn 告警；`apply` 被抑制并明示；`fail` 抛异常终止启动） |
| `sync-settings` | 同上 | DEBUG | `warn`：告警列出漂移键；`apply`：推送投影 + 等待任务，涉 `filterableAttributes`/`sortableAttributes` 时额外告警全量重建代价；`fail`：抛异常终止启动 |

代价提醒：修改 filterable/sortable 触发服务端**全量重建**，settings 同步只应发生在
启动/迁移期——生产环境建议 `create-if-missing` 起步、变更窗口显式切 `sync-settings` +
`apply`（详见[限制清单](limitations.md)）。

## 生命周期回调四件套

| 接口 | 触发点 | 签名语义 |
|---|---|---|
| `BeforeConvertCallback<T>` | `save`/`saveAll` 序列化前 | 返回改写后的实体 |
| `AfterSaveCallback<T>` | 写任务受理（wait-task 时含等待终态）后 | 对每个已保存实体触发 |
| `AfterLoadCallback<T>` | 读到原始 JSON、反序列化**前** | 入参/返回都是文档 JSON 文本 |
| `AfterConvertCallback<T>` | 实体反序列化后 | 返回改写后的实体 |

写链：`BeforeConvert → 序列化 → 写请求 →（可选等待任务）→ AfterSave`；
读链：`取回原始 JSON → AfterLoad → 反序列化 → AfterConvert`。同链多回调按注册顺序执行；
目标实体类型按泛型实参匹配（父子类型均可命中）。

声明为 bean 即生效：

```java
@Configuration(proxyBeanMethods = false)
class BookCallbacks {

    /** 写前规整书名：以命名类声明泛型实参（lambda 的泛型被 JVM 擦除，无法自动解析）。 */
    static final class TrimBookTitle implements BeforeConvertCallback<Book> {
        @Override
        public Book onBeforeConvert(Book entity, String indexName) {
            return entity.title() == null ? entity : /* 重建规整后的 record */;
        }
    }

    @Bean
    BeforeConvertCallback<Book> trimBookTitle() {
        return new TrimBookTitle();
    }
}
```

## 启动期 fail-fast 校验清单

以下任一情形都在应用启动时抛 `MeiliMappingException`（消息含类名/字段名/文件名定位），
不留运行期 surprise：

- 主键缺失、多于一个、或类型非 String/整型；
- 多个实体声明同一 `indexName`；
- `@MeiliField.name` 与 `@JsonProperty` 冲突；
- 显式 `searchableOrder` 重复；
- 透传文件缺失、非 JSON 对象、含白名单外键；
- 回调 bean 无法解析目标实体泛型（lambda 形态）。
