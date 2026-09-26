# Tasks

## 1. 基线清点与白名单起草

- [x] 1.1 生成现状扫描清单入变更记录：main/test 含中文注释文件清单（实测 13/83）、main 含中文消息文件清单（约 26）、断言中文消息子串的测试文件清单（`grep -rlP` 圈定），verify：三份清单落档且与扫描命令输出一致
- [x] 1.2 起草中文数据豁免清单（文件 + 用途 + 代表字面量），初版含 core spike IT（`三体`/`刘慈欣`/`北京`）、golden 透传资源、examples `data.json` 与 `meili/books.json`（中文 stopwords 数据），verify：清单覆盖 1.1 各文件内全部非注释中文行，白名单外零残留

## 2. main 运行期消息英文化（按模块推进，改完即测）

- [x] 2.1 core：异常消息与日志文案译英（保留 `{}` 占位符与类名/字段名/文件名定位信息，异常类型与触发条件不动），同步更新断言中文子串的测试，`mvn -s /home/lam/repo/settings.xml verify -pl meili-orm-core` 全绿
- [x] 2.2 repository：派生查询/注解查询配置异常与 WARN 日志译英 + 受影响测试断言同步，`verify -pl meili-orm-repository` 全绿（同上：95/95 + MeiliRepositoryIT，全绿）
- [x] 2.3 autoconfigure：`MeiliIndexInitializer` 等日志与异常译英 + 受影响 L2/IT 断言同步，`verify -pl meili-orm-spring-boot-autoconfigure` 全绿（同上：L2 41 + MeiliIndexInitializationIT，全绿）
- [x] 2.4 全仓 `src/main` 非注释行中文扫描（白名单外）零命中，verify：扫描命令输出为空（已验：`src/main/**/*.java` 含全角标点扫描零命中；数据串全角仅存于 test 白名单）

## 3. 本文源注释英文化（main）

- [x] 3.1 13 个 main 文件的中文注释/Javadoc（含 4 个 `package-info.java` 与 examples main）译英，语义随译不缩水（CLAUDE.md §5 深度分级维持），verify：main 注释行中文扫描零命中 + `javadoc` 门禁绿（终局 reactor javadoc show=private 全模块过）
- [x] 3.2 根/各模块 pom 与示例 yml 的 XML/`#` 注释及 `<description>` 译英（不引入被引用门禁禁止的内部材料引用），verify：pom/yml 全文中文扫描零命中（47 处/15 pom + 2 yml 全译）+ `check-source-citations.sh` 全绿 + selftest 绿 + `mvn validate` 通过

## 4. test 注释英文化

- [x] 4.1 83 个 test 文件约 524 行中文注释/Javadoc 译英（数据字面量与断言目标串不动——非消息断言的中文如 fixture 名称、注释里对中文样本值的提及改写为英文描述但样本值本体保留），verify：test 注释行中文扫描零命中（含行尾注释与全角标点两档扫描均零命中）
- [x] 4.2 全仓 Java 注释中文扫描（main+test）与本文源（pom/yml）中文扫描统一复跑，verify：除 1.2 白名单外全部零命中（终扫三档：main 任意 CJK=0、test 注释含全角标点=0、pom/yml=0；白名单数据串原位保留）

## 5. License 头与门禁

- [x] 5.1 根 pom `pluginManagement` 钉 com.mycila:license-maven-plugin（4.x 最新稳定，版本入 pom 即记录→实施落点 5.1.2，见 evidence），新增 `etc/license-header.txt`（版权行 `Copyright 2026 the original author or authors.`），verify：`mvn validate` 在插件激活前先本地拉取成功（已验：aliyun 镜像解析 5.1.2 成功、隔离 `-N license:check` 在根构建 SUCCESS）
- [x] 5.2 插件激活于根 build（`check` 绑定 validate 阶段、includes `**/*.java`、SLASHSTAR_STYLE），执行 `license:format` 批量插头，verify：`git diff --stat` 恰为 176 个 Java 文件 ✓ + 根 reactor `clean verify` 全绿（6.3 同跑确认：15/15 SUCCESS，license:check 绑 validate 生效）
- [x] 5.3 package-info 与带注解首行文件抽查（注解在 `package` 前的文件头部位次正确、javadoc 门禁无一失效），verify：全仓首行非 package/import/annotation 检查零命中 + 抽查 core/package-info 头部位次正确 + check 门禁负探针（篡改版权行 → validate 即红，还原后绿）

## 6. 仓库元数据与收尾

- [x] 6.1 根 pom 补 `<licenses>`（Apache-2.0）、`<scm>`（https://github.com/LamSpace/meili-orm）、`<url>`、`<developers>`，子模块不重复声明，verify：`mvn help:effective-pom -pl meili-orm-core` 含全部四段（已验：licenses/scm/url/developers 均 FOUND）
- [x] 6.2 `CLAUDE.md` §5 补"注释与运行期消息语言为英文（数据字面量白名单除外）"一句，verify：通读 §5 语义自洽（§5 第 4 条：英文语言约定 + License 头门禁指向）
- [x] 6.3 终局验证：根 reactor `clean verify` 全绿（CI 等价命令 `mvn -s ci/settings.xml -B clean verify` 本地复跑亦可）+ spec 三场景逐条核对（缺头注入试验红一次绿一次、扫描零命中、effective-pom）,verify：全部通过并留命令输出摘要于本文件勾选注记（BUILD SUCCESS 01:54min，15 模块全 SUCCESS，IT 39 run/0 fail/0 err，unit 全绿，三门禁 [javadoc/citations/license-check] 同跑绿，缺头篡改负探针已验）
