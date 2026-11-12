# 验证记录

2026-11-11 定向验证通过：执行下列 clean 构建，5 项迁移契约测试和 70 项集成测试全部成功，无失败、错误或跳过。集成测试使用独立 Testcontainers MySQL，不操作本地 gba-dev 数据库。

```powershell
mvn -f api/pom.xml clean verify '-Dtest=MigrationScriptContractTest' '-Dit.test=DevSeedMigrationIT,OrderPersistenceIT,OrderFlowIT,ShipmentSchemaIT,InventoryFlowIT,ShipmentFlowIT,ShipmentSoapIT'
```

