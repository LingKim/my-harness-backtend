# 后端工程

本目录是 Java 21 + Spring Boot 4.1 后端，提供 Web API、MyBatis-Plus 数据访问、Flyway 数据库迁移和可选的 Spring AI OpenAI-compatible 集成。

## 常用命令

```bash
./mvnw test
./mvnw spring-boot:run
```

## 默认行为

- 未加载根目录 `.env` 时，Flyway 默认关闭，不要求本机存在 MySQL。
- AI 默认关闭，不创建 `ChatModel`，也不会访问外部模型服务。
- `GET /api/health` 提供应用级健康检查。
- `/actuator/health` 提供基础设施健康检查。

## 启用数据库

启动根目录 Compose 服务后，在当前 shell 加载环境变量：

```bash
set -a
source ../.env
set +a
./mvnw spring-boot:run
```

Flyway migration 位于 `src/main/resources/db/migration/`，MyBatis-Plus XML 位于 `src/main/resources/mapper/`。真实业务 Mapper 可以按需继承 `BaseMapper<T>`；当前基础工程不创建虚假的实体或空 Mapper。

## 启用 AI

设置以下服务端变量：

```dotenv
AI_ENABLED=true
AI_BASE_URL=https://your-provider.example
AI_API_KEY=your-local-secret
AI_MODEL=your-model-name
```

启用时缺少任一必要值都会导致启动失败。不要在代码、测试、文档或日志中提交真实 API Key。
