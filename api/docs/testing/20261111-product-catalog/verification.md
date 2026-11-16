# 验证记录

原正文所指的接口契约与前端调用差异见 [本目录 README](README.md#product-contract)。

2026-11-11 第 4 节验证：`mvn -f api/pom.xml '-Dtest=InventoryAvailabilityQueryServiceTest,ProductCacheStoreTest,ProductRequestsTest,ProductAvailabilityIT,ProductPersistenceIT,InventoryFlowIT' test` 共 45 项通过，无失败/错误/跳过；全部数据库操作发生于隔离 Testcontainers。IDEA 构建通过；未执行本地 dev 数据库升级或第 5 节及后续任务。

2026-11-11 任务 4.5–4.7 定向验证：`mvn -f api/pom.xml '-Dtest=PublicCatalogSecurityTest,AuthContextTest,AuthInputTest,ProductRequestsTest,ProductCacheStoreTest,ProductAvailabilityIT,ProductPersistenceIT' test` 共 34 项通过，无失败/错误/跳过。MySQL/Redis 使用隔离 Testcontainers；从实际 Springdoc 导出的 `api/target/product-v1.openapi.yaml` 已同步至 `docs/api/product-v1.openapi.yaml`。前端代码未修改，待队友同步的调用差异见接口契约一节；未执行第 5、6、7 节任务、全量 clean verify 或本地 dev 升级。
