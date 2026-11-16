# 订单授权、SOAP 与成交快照整合

此记录对应 `align-commerce-module-integration` 第 5 节（2026-11-11，Asia/Shanghai），补充原 [物流验收记录（本目录入口）](README.md#verification)；不改写原 change 的完成状态或历史测试结果。订单服务契约见 [订单模块说明（本目录入口）](README.md#order)，对外语义见 [REST/SOAP API（本目录入口）](README.md#api-design)。

REST 调用顺序：可信 Actor → `ShipmentQueryService.getForActor` → `OrderBackedOrderAccessGuard` → `OrderQueryService.locateForActor` → 按商户和订单 ID 查运单 → 按商户和运单 ID 查轨迹。消费者限本人，员工限本商户；未知、同商户他人和跨商户订单统一 404/10004，且拒绝时不读取运单或轨迹。合法订单无运单继续返回 `NOT_SHIPPED` 与空轨迹。

SOAP 调用顺序：`ShipmentServiceTokenInterceptor` 校验 SOAP Header 服务凭证 → `TenantResolver` 解析 merchantCode → `OrderQueryService.locateForService` 按 merchantId/orderNo 确认真订单 → 按真实 orderId 查询同商户物流。此路径不构造消费者 Actor；消费者 Bearer 放在 HTTP Authorization 或 SOAP Header 中均不能替代服务凭证。未知订单为 `NOT_FOUND` Fault；真实订单无运单返回 `NOT_SHIPPED`、真实 orderNo 与空轨迹，承运商/运单号/末次事件时间缺省。

订单详情继续读取 `trade_order` 和 `trade_order_item` 的持久化成交快照，不 JOIN 当前目录，不要求商品仍在售，不调用库存写操作。当前目录更名、改规格、调价和停售均不改变历史名称、规格、成交单价、数量或订单金额。商品/SKU 标识和同商户外键用于追溯及引用完整性，不用于重建历史展示。

实际验证命令、结果和范围见 [本目录 README](README.md#commerce-verification)。
