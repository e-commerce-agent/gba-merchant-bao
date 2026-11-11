# Proposal

## Why

商品、库存、订单和物流已经分别实现，但当前 dev 演示数据的 SKU/商品标识没有对齐，库存也未关联商品目录；部分服务边界仍直接依赖 Mapper 或可能在授权前读取物流。V5 会话表已经在本地 dev 库执行，继续按当前 V6 物流、V7 订单、V8 库存、V9 商品顺序启动无法保证从商品基础数据到订单和物流的完整依赖关系。

## What Changes

- 统一商品/SKU、库存、订单项和物流所使用的稳定演示 ID、订单号及商户归属；为库存补齐依赖真实 SKU 的 dev 数据，并保留跨租户隔离样例。
- 补齐模块间 Java 服务调用：库存按可信商户校验 SKU 存在及归属，商品可售状态与库存状态按模块职责聚合；物流在读取任何运单前先经订单服务验证 Actor 对订单的访问权。
- 在尚未执行的 V6–V9 结构迁移中增加同租户复合外键：库存到 SKU、订单项到所属商品/SKU、物流到订单；业务规则仍由领域 Service 校验。调整迁移与 repeatable dev seed 顺序，使 V5 后先建商品，再建库存、订单、物流；V1–V5 保持不变，并同步更新维护工具、测试和文档。
- 保留订单和物流的只读业务范围、订单项成交快照和现有接口语义；不引入消费者下单、支付、取消、自动库存预占/扣减/释放或 Agent 写操作。
- 明确本次只需兼容当前本地 dev 数据库（已执行到 V5）；不对旧 V6–V9 已执行的其他数据库提供原位重排兼容。

## Capabilities

### New Capabilities
- `commerce-module-integration`: 定义商品、SKU、库存、订单、物流之间的稳定关联、租户校验、模块服务调用及 dev 演示数据完整性。

### Modified Capabilities
无。既有电商只读范围、迁移不可变性和环境数据分层规则继续适用；本 change 仅新增跨模块集成契约。

## Impact

- 迁移与种子：`api/src/main/resources/db/migration/`、`db/devdata/`、Flyway locations 和 dev seed 注册/升级代码。
- 服务实现：`product`、`inventory`、`order`、`shipment` 的 Service、授权边界及内部调用契约。
- 测试：迁移顺序和升级验证、跨模块 Service/HTTP 集成、fixture 与 dev seed 稳定 ID 对齐。
- 文档：模块数据设计、迁移编号、dev seed 顺序和本地启动/验证说明。
