# 变更日志

本文件记录项目的所有值得关注的变更。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
发布后版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

[English](CHANGELOG.md)

## [未发布]

### 新增

- 全部 Java 源文件的 Apache-2.0 License 头，由构建期门禁强制
  （`license-maven-plugin:check` 绑定 `validate`；批量插入用 `license:format`）。
- 根 pom 开源仓库元数据（`licenses`、`scm`、`url`、`developers`）。
- 双语文档集：英文规范 `README.md` + 中文镜像 `README.zh-CN.md`，`docs/` 各指南英文规范 +
  `docs/zh-CN/` 中文镜像，每份文档顶部语言切换链接。
- `CONTRIBUTING.md`（构建命令、三道门禁、源码语言约定、维护者依赖升级清单）与本
  `CHANGELOG.md`，均为双语。
- 徽章：许可证、CI 状态、Java 基线、Spring Boot 双代、Meilisearch 服务端代际。
- README 新增「项目结构」小节，以带注释的目录树逐一说明顶层目录与构建模块。

### 变更

- `src/main` 的异常消息与日志文案、以及 main/test/examples 的全部注释/Javadoc 统一为英文
  （承担测试/演示**数据**语义的中文字面量按约定保留，见 `CONTRIBUTING.md`）。
- README 装配小节明示发布状态：构件尚未上架 Maven Central，从源码安装。消息文本不属于
  API 契约。
- 确立展示名约定：正文与标题用 "Meili-ORM"（GitHub 仓库同步更名），小写 `meili-orm` 仍作
  构件、包名与配置前缀 slug；README 徽章移到标题下方居中一行；概述小标题补图标统一命名
  （英文 "At a Glance"、中文「一览」）。

### 移除

- 内部过程材料（设计文档、原始实施计划、spike 实证档案、上游 issue 草稿）移入
  `docs/internal/`，不再出现在交付文档面；交付文档不以其为阅读前提。

### 发布清单（首个 Central 版本落地时）

追加 Maven Central 版本徽章、改写两份 README 的装配小节、将 `[未发布]` 替换为发布版本号
与日期。
