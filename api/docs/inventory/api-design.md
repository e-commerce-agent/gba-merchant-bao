# 库存接口

所有路径以 `/api/v1/inventory` 开头，需要有效 Bearer 凭据及 `MERCHANT_STAFF` 角色，包括两个 GET 查询与四个 POST 操作。消费者访问统一沿用库存模块现有 400/13006 角色拒绝语义，未认证返回 401；拒绝时不返回精确数量或分页数据。商户及操作人只来自服务端认证的 Actor；body 或查询参数中的商户值不能替代登录身份。

旧商户前缀路径已停用，不保留别名。新路径仍受 `/api/v1/**` 认证链保护，精确库存的员工角色检查在 Service 读取库存前执行。数据库 `merchant_id`、商户/SKU 唯一键和同商户外键保持不变，本次路径统一无需数据库迁移。

响应使用 `ApiResponse{code,message,data,requestId}`，成功 code=0；四个 POST 成功均为 HTTP 200、data=null。

## 现有路由

| 方法 | 后缀 | 请求/结果 |
| --- | --- | --- |
| POST | `/adjust` | `idempotentKey,skuId,adjustQty,remark?`；非零正数入库、负数盘亏 |
| POST | `/reserve` | `idempotentKey,skuId,qty,bizId?,remark?`；可售 -qty、预占 +qty |
| POST | `/release` | 同 reserve；可售 +qty、预占 -qty |
| POST | `/deduct` | 同 reserve；预占 -qty，可售不变 |
| GET | `/{skuId}` | 本商户库存 `{skuId,availableQty,reservedQty,totalQty}`；totalQty=availableQty+reservedQty |
| GET | `/page?pageNo=1&pageSize=20` | `{records,total,current,size}`；pageNo 最小 1，pageSize 限制为 1–100 |

`qty` 必须为正数；`idempotentKey` 必填且非空。幂等键沿用全局唯一约定，每个独立业务操作使用不同键。`bizId` 只是可空追溯信息，不驱动订单状态或自动交易流程。

## 目录状态与首次调整

| 本商户目录/库存状态 | 正向 adjust | reserve | 负向 adjust / release / deduct |
| --- | --- | --- | --- |
| ACTIVE SKU，商品为 DRAFT/UNPUBLISHED/PUBLISHED | 允许备货，缺行从 0 初始化 | 需要已有库存及足够可售量 | 需要已有库存及足够对应数量 |
| DISCONTINUED SKU | 409/13008，包括缺行时 | 409/13008 | 允许已有库存/预占收尾，数量约束不变 |
| 未知或跨商户 SKU | 404/13001 | 404/13001 | 404/13001 |

商品未公开不等于 SKU 不存在；备货不使商品自动发布。只有正向 adjust 可首次初始化，其他操作缺行仍为 404/13001。停售后 release 会把已有预占转回可售量，这是已有预占的收尾，不属于新增供应。

首次正向 adjust 示例（为本商户真实 ACTIVE SKU 入库 6 件）：

```json
{"idempotentKey":"stock-adjust-unique-operation-id","skuId":"2600000000000302","adjustQty":6,"remark":"发布前备货"}
```

缺行时在单个库存事务内创建零行、调整到 available=6/reserved=0/version=1，并记录 before_available=0/after_available=6 的一条流水。初始化失败或流水写入失败均回滚；并发首次操作通过商户/SKU 唯一键与现有新事务重试协调。

身份及当前目录校验发生在幂等处理之前。相同键的 SUCCESS 重放按成功返回且不重复修改；当前 SKU 已停售的 reserve/正向 adjust 仍返回 13008。PROCESSING 返回 13004；FAILED 可用同一键抢回并重试。乐观锁每轮使用独立事务重新读数、校验，最多 3 轮。

## 业务错误码

| HTTP | code | 含义 |
| --- | --- | --- |
| 404 | 13001 | 本商户 SKU 不存在，或该操作要求的库存行不存在；跨商户统一相同结果 |
| 409 | 13002 | 可售量不足，无法预占 |
| 409 | 13003 | 库存并发冲突重试耗尽 |
| 409 | 13004 | 同一幂等请求处理中 |
| 409 | 13005 | 预占量不足，无法释放/扣减 |
| 400 | 13006 | 请求参数不合法，或库存访问主体不是商户员工 |
| 409 | 13007 | 人工调整后可售库存小于 0 |
| 409 | 13008 | 停售 SKU 不允许新增供应或新增预占 |
| 500 | 13999 | 库存内部/存储异常 |

拒绝目录引用或停售新增供应时，不创建或修改库存、流水。未新增初始化路由、消费者交易路由、订单写接口或 Agent 写操作。
