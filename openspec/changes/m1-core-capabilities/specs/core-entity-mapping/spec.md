## Purpose

声明式实体映射契约：注解如何声明索引、主键与字段角色，元模型如何解析（含嵌套点路径与 record），以及启动期校验何时以何种异常失败。

## ADDED Requirements

### Requirement: 注解集声明索引与主键

实体 SHALL 以 `@MeiliDocument(indexName)` 声明目标索引、以恰好一个 `@MeiliId` 声明主键属性；主键类型仅允许 String 与整型系（Long/Integer/int/long 等）。POJO 与 record 两形态 SHALL 同等支持。

#### Scenario: record 形态解析

- **WHEN** 对 `record Book(@MeiliId Long id, @MeiliField(searchable=true) String title)` 构建元模型
- **THEN** 索引名、主键路径 `id`、字段角色按注解呈现，与等价 POJO 结果一致

#### Scenario: 主键缺失即拒

- **WHEN** 实体无任何 `@MeiliId` 属性（或声明了多个）
- **THEN** 抛 `MeiliMappingException`，消息含类名与"@MeiliId"字样

#### Scenario: 主键类型非法即拒

- **WHEN** `@MeiliId` 标注在 Double/String以外非整型属性上
- **THEN** 抛 `MeiliMappingException`，消息含"主键"与非法类型名

### Requirement: 字段投影名与 Jackson 序列化一致

字段的文档投影名 SHALL 按"@MeiliField.name 优先、其次 @JsonProperty、再次 Java 字段名"唯一确定；`@MeiliField.name` 与 `@JsonProperty` 同时声明且不一致时 SHALL 启动期抛出 `MeiliMappingException`；`@JsonIgnore` 字段 SHALL 从元模型排除（不自造 transient 注解）。

#### Scenario: 改名冲突即拒

- **WHEN** 某字段同时有 `@MeiliField(name="a")` 与 `@JsonProperty("b")`
- **THEN** 构建该实体元模型抛 `MeiliMappingException`，消息含冲突双方名字与字段名

#### Scenario: JsonIgnore 排除

- **WHEN** 实体含 `@JsonIgnore` 字段
- **THEN** 属性列表中不存在该字段的任何点路径

### Requirement: 嵌套属性展平为点路径

非简单类型属性 SHALL 递归展平为 `parent.child` 点路径并继承子属性上的角色注解；递归 SHALL 有访问集防环与深度上限（≤3），static/synthetic 字段 SHALL 跳过。

#### Scenario: 嵌套 filterable 投影路径

- **WHEN** `Book` 含 `Author author`，`Author` 含 `@MeiliField(filterable=true) String city`
- **THEN** 元模型属性列表含 jsonPath 为 `author.city` 且 filterable=true

#### Scenario: 自引用类型终止

- **WHEN** 实体含同类型字段形成环（`Node.next`）
- **THEN** 元模型构建正常终止且属性列表非空

### Requirement: 元模型缓存与稳定访问

`MeiliMappingContext` SHALL 按 Class 缓存实体元模型（同一 Class 两次获取为同一实例）并支持并发访问；属性列表 SHALL 按 jsonPath 稳定排序；`idValue(entity)` SHALL 支持 getter/record 访问器/字段回退三种读取路径。

#### Scenario: 缓存同实例

- **WHEN** 对同一 Class 连续两次 `getEntity`
- **THEN** 返回同一 `MeiliPersistentEntity` 实例（`isSameAs`）

### Requirement: 映射异常体系

映射层校验失败 SHALL 统一抛 `MeiliMappingException`（继承非受检根异常 `MeiliOrmException`），消息 SHALL 含实体类名与涉事字段/文件名，保证只读异常即可定位。

#### Scenario: 透传文件缺失

- **WHEN** `@MeiliSetting(settingPath)` 指向不存在的 classpath 资源且触发投影
- **THEN** 抛 `MeiliMappingException`，消息含该 settingPath 原文
