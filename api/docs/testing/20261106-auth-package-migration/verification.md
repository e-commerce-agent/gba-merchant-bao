# auth 包迁移验证

2026-11-06 包迁移验证：29 个迁移类的方法逻辑保持；`mvn -f api/pom.xml clean test` 通过 44 项单元/契约测试，随后 `mvn -f api/pom.xml verify` 通过 62 项隔离容器集成测试，均无失败或跳过。AuthPersistenceIT 验证真实 Mapper/XML，AuthDevManualIT 验证九个 Springdoc 操作，AuthFlowIT 验证认证/会话行为。报告在 `api/target/surefire-reports` 与 `api/target/failsafe-reports`。
