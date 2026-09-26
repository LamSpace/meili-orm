# spike 结论：spring-data-commons 3.5.13 ↔ 4.0.3 桥接可行性定案

- 日期：2026-09-26 · 环境：JDK 25.0.3 / Maven 3.9.16（-s /home/lam/repo/settings.xml）/ commons 3.5.13 + 4.0.3（本地仓库）
- 判据：design D-2 三条（① 桥原型 + 20 方法名黄金集在 3.5.13 绿；② 同源码在 4.0.3 运行无链接错误；③ 锚点类 javap 签名 diff 归档）

## 结果总览

| 判据 | 结果 |
|---|---|
| ③ 锚点 javap diff | **路线 (a) 证伪**（见下漂移清单）；domain/config/EntityInformation/PartTree 结构面零漂移 |
| ① 黄金集 26 断言 @ commons 3.5.13 | **绿**（路线 (b)：自研语法解析器 + 反射字典属性切分 + core 投影名桥） |
| ② 同一份源 @ commons 4.0.3 运行 | **绿**（复制源入 `it/meili-orm-it-boot4` 直接运行，无 `NoSuchMethodError`/`NoClassDefFoundError`） |

**裁决：单模块 + 路线 (b)（语法自研、语义桥到 core）；commons 薄变体不需要。**

## 关键证据：为什么"commons PartTree/PropertyPath 桥"（路线 a）不可双代

1. `org.springframework.data.repository.query.parser.Part` 的属性面**只有** `getProperty()`，且返回类型发生**代际迁移**：
   - 3.5.13：`public org.springframework.data.mapping.PropertyPath getProperty()`
   - 4.0.3：`public org.springframework.data.core.PropertyPath getProperty()`（`mapping.PropertyPath` 类在 4.0 jar 中不存在）
   方法描述符随返回类型入字节码 → 按 3.5 编译的任何 `part.getProperty()` 调用在 4.0 运行期 **NoSuchMethodError**；无任何不触碰该签名的字符串替代面（javap 实证 Part 无 `getPropertyParts()` 等成员）。
2. `RepositoryFactorySupport`（4.0 侧删改，diff=8 行）：`getTargetRepositoryViaReflection(Class, Object...)` 移除；`getQueryLookupStrategy(QueryLookupStrategy$Key, QueryMethodEvaluationContextProvider)` 移除（改为 `EvaluationContextProvider`/`ValueExpressionDelegate` 面）→ 继承该基类即代际绑定。
3. `RepositoryFactoryBeanSupport`（diff=7 行）：`isSingleton()`、`setEvaluationContextProvider(QueryMethodEvaluationContextProvider)` 等移除/改签名。
4. 零漂移面（可用于双代共用）：`PartTree`（18 行全同）、`RepositoryConfigurationExtensionSupport`（24 行全同）、`QueryLookupStrategy` 接口（4 行全同）、`EntityInformation`（7 行全同）、`org.springframework.data.domain.*`（`Sort/PageRequest/PageImpl/Pageable` 行为断言在两代下各跑一轮绿）。
5. `QueryMethod` 仅增量（4.0 加 `isSearchQuery()`），但其属性面经由 `Parameters`/方法名解析，且构造绑定 `RepositoryFactorySupport` 流程 → 不采用。

## 采用的替代形态（路线 b 实证要点）

- 方法名语法自研（`SpikeNameParser` 原型）：动词/`Top N`/`Distinct`/`By`/`OrderBy…Asc|Desc`/`And|Or` 大写定界/`Not` 前缀与后缀/关键字尾缀表（含 LT/LTE/GT/GTE/BETWEEN/IN/TRUE/FALSE/CONTAINING/LIKE/EQ/NE）；不支持面（StartingWith/EndingWith/Regex/Null/Empty/Exists/IgnoreCase）显式抛 `UnsupportedOperationException`。
- 属性段用**实体反射字典最长前缀切分**（`AuthorCity → [author, city]`），**属性缩写不支持**（`findByAdrCity` 抛错并明示）；随后 `MeiliNames.docName` + core 规则落投影名（`book_title`/`author.city`；`@JsonIgnore`/聚合叶/未知属性均为可定位错误）。
- 工厂形态：不继承 `RepositoryFactorySupport`/`RepositoryFactoryBeanSupport`；`FactoryBean + InvocationHandler` 自持代理，`@Enable` 侧用零漂移的 `RepositoryConfigurationExtensionSupport`。
- 黄金集 26 条（19 正例/6 负例/1 commons 面链接）在 3.5.13 与 4.0.3 双代全绿；正式实现时以本 spike 源为底落入 `src/main`（任务 3.1/3.2/3.5）与仓库代理（任务 2.2）。

## 遗留与影响

- 风险降级：矩阵 Repository IT（任务 5.1）继续承担"实现面扩大后漂移再犯"的常驻哨兵。
- 规格回写：derived-queries 的"投影名桥接"需求措辞由"commons PropertyPath 解析"改为"自研语法 + 反射字典切分"，并把"缩写不支持"并入不支持面清单；repository-layer 的工厂需求去掉"继承 RepositoryFactorySupport"表述。
- spike 源保留于 `meili-orm-repository/src/test/.../spike/` 与 `it-boot4` 复制件，作为常驻回归哨兵（对齐 SpikeAJsonHandlerIT 惯例）。
