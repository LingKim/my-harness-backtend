# MyBatis-Plus Mapper 约定

- Java Mapper 接口统一放在对应业务能力的 `mapper` 包中，并使用 `@Mapper` 标记。
- 需要通用 CRUD 时，真实业务 Mapper 可以按需继承 `BaseMapper<T>`；没有业务实体时不创建空 Mapper。
- XML 文件统一放在本目录的业务子目录中，路径由 `classpath:/mapper/**/*.xml` 扫描。
- XML 的 `namespace` 必须与 Java Mapper 接口全限定名完全一致。
- 数据库列使用 `snake_case`，Java 属性使用 `camelCase`，由 MyBatis-Plus 自动转换。
- 数据库结构只能通过 `db/migration/` 中的 Flyway 脚本修改，Mapper 不负责建表。
