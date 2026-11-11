# 订单授权、SOAP 与成交快照整合

此记录对应 `align-commerce-module-integration` 第 5 节（2026-11-11，Asia/Shanghai），补充原 [物流验收记录](verification.md)；不改写原 change 的完成状态或历史测试结果。订单服务契约见 [订单模块说明](../order/README.md)，对外语义见 [REST/SOAP API](api-design.md)。

REST 调用顺序：可信 Actor → `ShipmentQueryService.getForActor` → `OrderBackedOrderAccessGuard` → `OrderQueryService.locateForActor` → 按商户和订单 ID 查运单 → 按商户和运单 ID 查轨迹。消费者限本人，员工限本商户；未知、同商户他人和跨商户订单统一 404/10004，且拒绝时不读取运单或轨迹。合法订单无运单继续返回 `NOT_SHIPPED` 与空轨迹。

SOAP 调用顺序：`ShipmentServiceTokenInterceptor` 校验 SOAP Header 服务凭证 → `TenantResolver` 解析 merchantCode → `OrderQueryService.locateForService` 按 merchantId/orderNo 确认真订单 → 按真实 orderId 查询同商户物流。此路径不构造消费者 Actor；消费者 Bearer 放在 HTTP Authorization 或 SOAP Header 中均不能替代服务凭证。未知订单为 `NOT_FOUND` Fault；真实订单无运单返回 `NOT_SHIPPED`、真实 orderNo 与空轨迹，承运商/运单号/末次事件时间缺省。

订单详情继续读取 `trade_order` 和 `trade_order_item` 的持久化成交快照，不 JOIN 当前目录，不要求商品仍在售，不调用库存写操作。当前目录更名、改规格、调价和停售均不改变历史名称、规格、成交单价、数量或订单金额。商品/SKU 标识和同商户外键用于追溯及引用完整性，不用于重建历史展示。

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

第 6 节的迁移路径综合验证、完整 `clean verify` 以及第 7 节的本地 dev 升级验收另行执行；本记录只覆盖第 5 节。订单与物流仍仅提供既有查询入口，没有下单、支付、取消或发货写接口。
