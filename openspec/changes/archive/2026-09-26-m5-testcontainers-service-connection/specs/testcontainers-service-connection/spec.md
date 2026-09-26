# Spec Delta

## Purpose

定义 meili-orm 的 Testcontainers 集成能力：类型化 MeiliSearch 服务容器的默认装配契约，以及经 Spring Boot 服务连接机制把容器连接信息自动桥接为连接详情 bean 的行为边界与优先级。

## ADDED Requirements

### Requirement: 类型化 MeiliSearch 服务容器

系统 SHALL 提供类型化容器：默认镜像钉 `getmeili/meilisearch:v1.49.0`、暴露端口 7700、注入 master key 环境变量并以健康端点就绪为启动等待条件；镜像与密钥 SHALL 可通过容器 API 覆盖。容器 SHALL 提供访问其外部 URL 与所配密钥的方法，供桥接与测试断言取用。

#### Scenario: 默认容器健康就绪

- **WHEN** 在具备 Docker 的环境启动默认配置的容器实例
- **THEN** 容器就绪后其 URL 可访问健康端点且认证使用所配 master key

#### Scenario: 覆盖生效

- **WHEN** 以自定义镜像标签与密钥构造容器
- **THEN** 容器的镜像与密钥读取值为自定义值

### Requirement: 服务连接桥接

在引入本模块且运行环境 Boot 代际支持第三方服务连接扩展时，测试上下文声明带服务连接注解的容器字段 SHALL 使系统注册一个连接详情 bean，其 URL 与密钥与容器实际映射端口及所配密钥一致，下游客户端装配无需任何手写配置即指向该容器。

#### Scenario: 容器接入上下文完成往返

- **WHEN** 真机 Docker 环境下测试类以服务连接注解声明容器并启动上下文
- **THEN** 产出的 SDK Client 指向容器 URL（可经构建前 Config 观测），保存后按主键读取往返成功

#### Scenario: 用户连接详情 bean 优先

- **WHEN** 测试上下文同时声明用户自有连接详情 bean 与服务连接容器
- **THEN** 桥接退避，Client 使用用户 bean 的连接信息
