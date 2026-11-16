# 验证记录

定向验证使用隔离 Testcontainers MySQL/Redis，不连接本地 dev 数据库：

| 测试 | 验证边界 |
| --- | --- |
| `OrderQueryServiceTest` | 本人/员工定位成功；他人、跨商户、未知统一 404；服务间定位只用商户和订单号 |
| `ShipmentQueryServiceTest` | 先订单授权后运单/轨迹；拒绝时无物流读取；存储异常不伪装为缺失；SOAP 先定位订单 |
| `ShipmentServiceTokenInterceptorTest` | SOAP Header 服务凭证校验 |
| `ShipmentFlowIT` | 真实 REST 权限、已有运单的他人订单仍拒绝、合法无运单、员工/本人和两租户隔离 |
| `ShipmentSoapIT` | 有运单、合法无运单、未知订单、同号两租户、无效和消费者凭证 |
| `OrderFlowIT` / `OrderPersistenceIT` | 目录变更后的成交快照、库存服务无调用、订单原有授权和持久化回归 |

实际执行命令：

```powershell
mvn -f api/pom.xml '-Dtest=OrderQueryServiceTest,ShipmentQueryServiceTest,ShipmentServiceTokenInterceptorTest' '-Dit.test=OrderFlowIT,OrderPersistenceIT,ShipmentFlowIT,ShipmentSoapIT' test-compile surefire:test failsafe:integration-test failsafe:verify
```

2026-11-11 20:20:58（Asia/Shanghai）完成，`BUILD SUCCESS`，耗时 2 分 20 秒：16 项单元测试、29 项集成测试，失败/错误/跳过均为 0。各类数量依次为 3、9、4、5、5、9、10。原始日志位于本地忽略目录 `api/target/commerce-section5-verification.log`，JUnit XML 位于 `api/target/surefire-reports/` 与 `api/target/failsafe-reports/`。初次沙箱内执行因 Docker 命名管道拒绝访问而失败，授权后在沙箱外运行上述隔离测试成功；未改用开发库或跳过集成测试。

静态核对：`product/inventory/order/shipment` 下没有跨域 Mapper import；订单 Controller 仍只有列表/详情 GET，物流 Controller 仍只有按订单查物流 GET；`git diff --check` 与 `openspec validate align-commerce-module-integration --strict` 通过。

新增第 6 节统一库存主键为雪花 ID；迁移路径综合验证及完整 `clean verify` 顺延到第 7 节，本地 dev 升级验收顺延到第 8 节并另行执行。本记录只覆盖第 5 节；库存主键规则见 [库存模块（本目录入口）](README.md#inventory)。订单与物流仍仅提供既有查询入口，没有下单、支付、取消或发货写接口。
