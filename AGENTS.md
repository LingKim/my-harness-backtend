# ChinaMate 后端 AI Coding 规则

本文件适用于当前后端仓库。后端作为主 AIWorkSpace 的 submodule 使用时，主仓库根级 `AGENTS.md` 与本文件同时生效；本文件不得降低根级 OpenSpec、安全、验证、Git 或 submodule 规则。

## 1. 事实优先级

处理后端任务时按以下顺序判断：

1. 主仓库 `openspec/specs/` 与已经确认的活动 change。
2. 本文件、[后端架构文档](docs/architecture.md) 和 `README.md`。
3. 当前 `pom.xml`、源码、测试、Flyway migration 与真实命令输出。
4. Java 21、Spring Boot 4.1、MyBatis-Plus 3.5.17、MySQL 8.4 和 Spring AI 2.0 当前文档。
5. 通用最佳实践和代理既有知识。

如果文档与代码、测试或当前依赖冲突，先核实变更背景；不得根据旧教程覆盖已验证的项目事实。

## 2. 模块化单体边界

项目保持一个 Maven module、一个 Spring Boot 进程和一个可执行产物。业务代码按顶层 package 定位：

- `account`：账号、凭据、会话、用户偏好和旅行上下文。
- `guide`：结构化攻略、双语版本、来源、收藏和反馈。
- `assistant`：AI 会话、消息、知识引用、风险、额度和解决反馈。
- `community`：问题、回答、单层评论、最佳回答和解决状态。
- `support`：AI 转社区草稿、官方求助渠道和升级流程编排。
- `moderation`：举报、审核、内容处置、账号限制和审计记录。
- `notification`：站内通知和未读状态。
- `media`：图片校验、存储引用、保留期限和删除编排。

`ProjectApplication`、`config` 和 `health` 是技术入口，不是业务模块。不要建立全局 `controller/`、`service/`、`mapper/` 或 `entity/` 目录。

## 3. 模块内部职责

模块内部只在出现真实类型时按需创建：

- `api`：HTTP Controller、Request、Response、协议校验和身份上下文转换。
- `application`：用例编排、命令、查询、事务边界、应用事件和外部端口。
- `domain`：领域对象、值对象、业务状态转换、策略和仓储端口。
- `infrastructure`：MyBatis、Spring AI、对象存储及其他外部适配器。

依赖规则：

1. 主要方向为 `api → application → domain`。
2. `infrastructure` 实现 `application` 或 `domain` 定义的端口，不依赖 `api`。
3. `domain` 不依赖 Spring MVC、MyBatis、Spring AI、`api`、`application` 或 `infrastructure`。
4. Controller 不直接调用 Mapper、数据库行对象或 `ChatModel`。
5. API DTO、应用结果、领域对象和数据库行对象不得混用。
6. 不为每个用例机械创建接口和 `Impl`；只有真实替换边界才定义接口。

## 4. 跨模块协作

- 跨模块同步调用只能使用目标模块公开的 `application` 契约。
- 禁止访问其他模块的 `api`、`domain`、`infrastructure`、Mapper 或数据库行对象。
- 跨域流程优先由 `support` 等明确的编排模块发起，禁止两个模块双向调用。
- 核心业务使用同步调用；通知和埋点等副作用可以在事务成功后触发，但不得自行引入消息队列。
- 事务保持短小，外部模型、网络、文件或对象存储 I/O 不得放在数据库事务内。

## 5. `shared` 准入规则

只有同时满足以下条件的技术能力才能进入 `shared`：

1. 至少被两个模块使用。
2. 在各模块中的语义相同。
3. 没有合理的单一业务归属。

业务对象、业务枚举、业务 DTO、Mapper、仓储、`BaseService`、`BaseController` 和无明确语义的 `Utils` 禁止进入 `shared`。首次出现的辅助代码保留在所属模块，不为未来可能复用提前抽象。

## 6. 数据与外部能力

- 数据访问只使用 MyBatis-Plus，不得引入 Spring Data JPA、JPA entity、`JpaRepository`、Criteria API 或 `@DataJpaTest`。
- 数据库结构只由 `src/main/resources/db/migration/` 中新的 Flyway migration 管理。
- MyBatis XML 放在 `src/main/resources/mapper/<module>/`，查询显式列出字段，业务值使用 `#{}` 绑定。
- `${}` 仅允许用于 JDBC 无法参数化且来自服务端封闭白名单的标识符或 SQL 片段。
- Prompt 放在 `src/main/resources/prompts/<module>/`，不得将大段 Prompt 散落在 Controller。
- Spring AI 和对象存储通过模块基础设施适配器接入，密钥不得写入代码、测试、文档、接口或日志。

## 7. 安全与测试

- 必需依赖使用构造器注入和 `private final` 字段。
- Controller 使用 Bean Validation；异常通过统一错误契约处理。
- 密码必须使用业界认可的自适应单向哈希，禁止保存、返回或记录明文密码及密码摘要。
- 服务默认无状态；事务放在最小必要的应用用例边界。
- 业务变更遵循 RED → GREEN → REFACTOR，并同步补充自动化测试。

验证命令：

```bash
# 仅运行架构规则
./mvnw -Dtest=ArchitectureRulesTests test

# 运行完整后端测试
./mvnw test

# 检查空白与冲突标记等差异问题
git diff --check
```

架构测试是静态边界门禁，不替代业务测试、数据库集成测试、安全测试或真实运行验证。修改代码后默认不运行无关 build，但必须运行与变更风险相称的测试。

## 8. 文档与 Git

- 新建或修改的人类可读文档和代码注释默认使用中文；标识符、命令、路径和协议字段保留原文。
- 新增模块或修改边界时，同步更新 `docs/architecture.md` 和架构测试。
- 未经用户明确要求，不提交、推送、合并、删除分支或执行破坏性 Git 操作。
- 作为 submodule 修改时，先在后端仓库完成验证、提交并推送，再由主仓库更新 gitlink；每一步都需要对应授权。
