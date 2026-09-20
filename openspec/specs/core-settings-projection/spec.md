# core-settings-projection Specification

## Purpose
实体注解到 MeiliSearch settings 的投影管线契约：角色数组的生成规则、透传文件的合并与白名单、输出格式的字节级稳定性。投影 SHALL 为纯函数（同实体输入恒等输出），运行期零副作用。
## Requirements
### Requirement: 不标注 = 不声明

四个角色数组（searchable/filterable/sortable/displayed）SHALL 仅在该实体至少有一个字段声明对应角色时生成；无声明的角色 SHALL 输出为"键不存在"（null），任何情况下不生成空数组、不无中生有覆盖服务端默认。

#### Scenario: 全无角色

- **WHEN** 实体仅有 `@MeiliId` 无任何角色注解
- **THEN** 投影 `hasAny()` 为 false，四数组均为 null，toJson 产物不含任何角色键

#### Scenario: displayed 白名单后果可见

- **WHEN** 仅一个字段声明 `displayed=true`
- **THEN** displayedAttributes 数组只含该字段（服务端将隐藏其余字段的后果在 Javadoc 与产物注释中声明）

### Requirement: searchable 顺序规则

searchableAttributes SHALL 按"显式 `searchableOrder>=0` 升序在前，未指定者按 jsonPath 字典序追加"排列；其余三数组 SHALL 按 jsonPath 字典序稳定输出。

#### Scenario: 显式序优先于字典序

- **WHEN** `overview`（order=-1）与 `book_title`（order=1）均 searchable
- **THEN** 数组为 `["book_title","overview"]`

### Requirement: 透传 settings 合并与白名单

`@MeiliSetting(settingPath)`（可重复，按声明序）指向的 classpath JSON SHALL 深合并进投影且**透传优先**（透传中出现的键覆盖同名投影键，含四大数组）；透传键 SHALL 限定于已知 MeiliSearch settings 键白名单，未知键 SHALL 以 `MeiliMappingException` 拒绝且消息含非法键名与来源文件路径。

#### Scenario: 透传覆盖投影

- **WHEN** 透传文件声明了 `searchableAttributes`
- **THEN** 投影结果中该键以透传值生效

#### Scenario: 未知键拒绝

- **WHEN** 透传文件含 `totallyBogus` 键
- **THEN** 投影抛 `MeiliMappingException`，消息含 `totallyBogus` 与文件路径

### Requirement: 输出格式字节稳定（golden 锁定）

`ProjectedSettings.toJson()` SHALL 以固定键序（四角色数组在前、透传键按字典序）与稳定 pretty-print 输出；M1 SHALL 以逐字节 golden 文件测试锁定该格式（格式变更必须显式改 golden，视为契约变更）。

#### Scenario: golden 逐字节一致

- **WHEN** 对固定注解实体执行投影并 toJson
- **THEN** 输出与 `golden/books-settings.json` 逐字节相等（trim 后）

