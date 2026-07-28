# MyBatis-Plus Mapper 约定

- Java Mapper 接口统一放在对应业务能力的 `mapper` 包中，并使用 `@Mapper` 标记。
- 需要通用 CRUD 时，真实业务 Mapper 可以按需继承 `BaseMapper<T>`；`BaseMapper<T>` 自动 CRUD 不重复编写 XML，没有业务实体时不创建空 Mapper。
- 新增或实质修改的自定义 SQL 必须写入 XML；Java Mapper 接口不得使用 SQL 注解或 Provider 注解承载 SQL，`@Mapper`、`@Param` 等非 SQL 注解继续允许。
- XML 文件统一放在本目录的业务子目录中，路径由 `classpath:/mapper/**/*.xml` 扫描。
- XML 的 `namespace` 必须与 Java Mapper 接口全限定名完全一致，statement ID 必须与接口方法对应，业务值必须通过 `#{}` 安全绑定。
- 后续 change 修改存量注解 statement 的 SQL 文本、参数、结果映射或数据库行为时，必须在同一 change 中迁入 XML；纯注释、格式或与 SQL 无关的 Java 修改不触发迁移。
- 数据库列使用 `snake_case`，Java 属性使用 `camelCase`，由 MyBatis-Plus 自动转换。
- 数据库结构只能通过 `db/migration/` 中的 Flyway 脚本修改，Mapper 不负责建表。
