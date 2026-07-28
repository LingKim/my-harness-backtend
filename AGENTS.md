# ChinaMate 后端 AI Coding 入口

本文件是后端 submodule 的 Codex 入口，只负责作用域、事实优先级和任务路由。项目 Agents、Rules 与 Skills 统一归属完整 AIWorkSpace 根目录 `../.codex/`；独立 clone 不具备完整治理上下文。

## 事实优先级

1. 完整 AIWorkSpace 中已确认的 OpenSpec 和活动 change。
2. 当前 `pom.xml`、源码、测试、Flyway migration、配置和真实命令输出。
3. 任务作用域内的 `../.codex/rules/backend-conventions.md` 与 `../.codex/rules/database-conventions.md`。
4. Java 21、Spring Boot 4.1、MyBatis-Plus 3.5.17 与 MySQL 8.4 当前官方文档。
5. 对应项目级 Skill。
6. 代理既有知识。

当前实现违反已确认规格或强制 Rule 时，必须报告差异并通过受控变更修正，不能把现状静默变成新规范。

## 任务路由

- 后端实现或评审优先使用 `../.codex/agents/backend_engineer.toml`；跨栈验收按需使用 `qa_engineer.toml`，实现完成后的 Spec 对账使用只读 `spec_reviewer.toml`。
- Java、Spring Boot、Web、配置、事务、日志、测试或安全：读取 `../.codex/rules/backend-conventions.md` 和项目维护的 `../.codex/skills/java-springboot/SKILL.md`。
- MySQL、Flyway、MyBatis XML、存量注解 SQL变更、schema、索引、查询、锁、连接或数据库运维：额外读取 `../.codex/rules/database-conventions.md` 与 `../.codex/skills/mysql/SKILL.md`，再按任务读取直接相关 reference。
- 常规业务持久化默认使用所属模块的 MyBatis-Plus Mapper/适配器；直接使用 Spring JDBC 必须有已确认 design 依据、限定范围、替代方案取舍和等价测试，缺少依据时停止实现。
- 新增或实质修改的自定义 SQL 必须使用 Mapper XML；`BaseMapper<T>` 自动 CRUD 继续允许，修改存量注解 statement 时按 Rule 在同一 change 中迁入 XML。
- 新增或改变 HTTP 契约、跨前后端行为或根级 OpenSpec：必须在完整 AIWorkSpace 中读取根 `AGENTS.md`、相关规格和 `docs/standards/api-development-guidelines.md`；独立 clone 不得推测跨栈契约。
- 如果 `../.codex/` 不存在，停止受治理的实现或评审并要求在完整 AIWorkSpace 中继续，不复制或推测 Rules 与 Skills。
- 纯文档或机械格式化：不强制加载通用 Skill，但仍遵守对应 conventions 的验证和安全规则。

## 权限与验证

- 未经用户明确要求，不提交、推送、合并、删除分支或执行破坏性操作。
- 修改前先运行 `git status --short --branch`，保护用户已有改动。
- Java 架构边界运行 `./mvnw -Dtest=ArchitectureRulesTests test`；业务行为运行相关测试；默认不运行无关 build。
- 后端或数据库完成声明必须依次提供开发工程实践合规清单、QA 独立验证和 Spec Reviewer 的项目 Rule/技术基线结论；尚未进入对应阶段时明确标为未运行。
- AI 治理文件变更运行 `bash scripts/check-agent-governance.sh` 和 `git diff --check`。
