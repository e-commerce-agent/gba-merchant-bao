# 库存模块（inventory）

库存模块（队友交付）按主仓规范合入：纯 MyBatis + XML（无 MyBatis-Plus），身份只来自
`@AuthenticationPrincipal Actor`（`auth.security.Actor`，record），统一主仓 `ApiResponse`
envelope，业务错误码走 13xxx 段。

## 资源与语义

- `POST /api/v1/merchant/inventory/reserve`：预占（可售 -qty、预占 +qty）
- `POST /api/v1/merchant/inventory/release`：释放预占（可售 +qty、预占 -qty）
- `POST /api/v1/merchant/inventory/deduct`：扣减预占（支付成功/发货出库）
- `POST /api/v1/merchant/inventory/adjust`：人工调整（正数入库、负数盘亏）
- `GET  /api/v1/merchant/inventory/{skuId}`：单个 SKU 库存（本商户）
- `GET  /api/v1/merchant/inventory/page?pageNo&pageSize`：分页列表（pageSize 上限 100）

匿名公开"有货/无货"并入商品详情聚合（商品模块调 `InventoryService.inStock`），不设公开 HTTP 端点。

## 关键设计

- **幂等**：`inventory_idempotent` 唯一键 + INSERT 抢占（不先查后写）；SUCCESS 重复请求按成功返回，
  FAILED 可 CAS 抢回重试，PROCESSING 返回 13004。
- **不超卖**：乐观锁 `version` 条件更新；每轮重试 `REQUIRES_NEW` 新事务重读最新数据、重新校验
  （RR 快照下同一事务重读无效），重试 3 次仍冲突返回 13003。
- **租户隔离**：所有 SQL 带 `merchant_id` 谓词；跨租户与不存在统一 404/13001。
- **流水留痕**：`inventory_transaction` 记录变更前后值、幂等键、操作人（accountId）。

## Flyway 编号

`V7__inventory_tables.sql` 在商品目录 `V6__product_module.sql` 后创建库存结构，并用同商户复合外键引用 `product_sku`。dev 种子为 `R__050_inventory_demo.sql`，依赖 `R__040_product_demo.sql`；只初始化缺失库存，重跑不覆盖人工数量/version，也不创建库存流水。V1–V5 与 R010/R020/R030 保持冻结。

库存及流水都用 `(merchant_id, sku_id)` 引用同商户 SKU，限制删除父 SKU，不级联删除流水。当前顺序只兼容新库及本地尚在 V5 的 dev 基线，不兼容已执行旧 V6–V9 布局的其他库。演示库存覆盖 SKU `2500000000000301–307/309`，含有货、缺货、预占和停售保留量；完整 ID 对照及补种步骤见 [dev 数据登记](../../src/main/resources/db/devdata/README.md)。

## 已知留待事项

- 主键沿用 AUTO_INCREMENT（模块内部自增）；API 输出如需字符串 ID 由 VO 层处理。
- 主键/雪花与商品、订单模块的 ID 约定统一时再整体调整。
