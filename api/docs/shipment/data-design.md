# 物流模块数据设计（评审稿）

> 状态：2026-11-07 评审稿已实现并落盘：`V6__shipment_tables.sql` 与 `R__040_shipment_demo_data.sql`（devdata 编号登记见 db/devdata/README.md）。编号已在 tasks 1.3 定稿：main 的 V5/R030 已被会话模块占用，物流顺延为 V6/R040。UTC 时间、Snowflake 主键、字符串化输出与 auth 模块一致。

## 1. 表：shipment（一单一运单）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | Snowflake |
| merchant_id | BIGINT UNSIGNED | NOT NULL | 租户根；所有查询必带 |
| order_id | BIGINT UNSIGNED | NOT NULL | 逻辑关联订单模块 trade_order.id；**不建物理外键** |
| order_no | VARCHAR(64) | NOT NULL | 下单时订单号快照；SOAP 查询键之一 |
| status | VARCHAR(20) | NOT NULL | NOT_SHIPPED / IN_TRANSIT / DELIVERED |
| carrier_name | VARCHAR(64) | NOT NULL | 承运商展示名 |
| carrier_code | VARCHAR(32) | NULL | 承运商编码 |
| tracking_no | VARCHAR(64) | NOT NULL | 运单号 |
| estimated_delivery_at | DATETIME | NULL | 预计送达；仅数据提供时返回 |
| delivered_at | DATETIME | NULL | 签收时间 |
| created_at | DATETIME | NOT NULL | UTC |
| updated_at | DATETIME | NOT NULL | UTC |

索引：

| 索引 | 服务于 |
| --- | --- |
| `UNIQUE(merchant_id, order_id)` | 按订单查运单（REST 主路径）、一单一运单约束 |
| `UNIQUE(merchant_id, tracking_no)` | 运单号租户内唯一防重 |
| `KEY(merchant_id, order_no)` | SOAP 按商户编码+订单号查询 |

## 2. 表：shipment_track（轨迹事件，只追加）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | Snowflake |
| shipment_id | BIGINT UNSIGNED | NOT NULL | 逻辑关联 shipment.id |
| merchant_id | BIGINT UNSIGNED | NOT NULL | 冗余租户列，隔离查询不 join |
| event_time | DATETIME | NOT NULL | 事件时间，UTC |
| status | VARCHAR(20) | NOT NULL | 事件时状态 |
| description | VARCHAR(255) | NOT NULL | 轨迹描述（脱敏） |
| location | VARCHAR(128) | NULL | 事件地点 |

索引：

| 索引 | 服务于 |
| --- | --- |
| `KEY(shipment_id, event_time)` | REST/SOAP 轨迹按时间升序读取 |
| `UNIQUE(shipment_id, event_time, description)` | devdata/种子幂等写入 |

## 3. 关联与隔离决策

1. **不建物理外键**：订单表由订单模块迁移创建，物理顺序未知；授权由 Service/SQL 强制（与 auth 一致），跨表一致性由唯一索引与种子约束保证。
2. **冗余 merchant_id** 到 shipment_track：轨迹隔离查询不 join 主表，SQL 一律带租户限定。
3. **冗余 order_no** 快照到 shipment：SOAP 按订单号查询免 join 订单表；订单号以下单时为准，商品/订单变更不回写。

## 4. Flyway 编号（已定稿）

- 本模块迁移为 `V6__shipment_tables.sql`，devdata 为 `R__040_shipment_demo_data.sql`。
- 定序结果：**物流 V6/R040 → 订单 V7/R050 → 库存 V8**。main 上 `V5__agent_conversation.sql` 与 `R__030_chat_demo.sql` 先占用，物流不能再取 V5。
- 因不建物理外键，物流表先于订单表创建不构成依赖问题；`order_id` 仅逻辑关联订单模块的 `trade_order.id`（V1 legacy 基线已占用 `orders`，订单表落地名为 `trade_order`）。
- 已发布迁移不修改（团队铁律）；本模块迁移仅 DDL。

## 5. 数据分层

| 层 | 内容 | 边界 |
| --- | --- | --- |
| devdata | `R__040_shipment_demo_data.sql`：两租户各含未发货、多段轨迹运输中、已签收三类演示 | 仅 dev 加载；只补缺失、幂等；test/prod 不加载 |
| test fixture | `src/test/resources/db/fixture/shipment.sql`：跨租户对照、越权场景、无物流记录订单 | 测试 `@Sql` 显式加载；事务回滚或稳定标识清理 |
| 生产 | 无种子；运单数据按实际业务写入 | 本期不建物流录入后台 |

## 6. Redis

物流模块本期不使用 Redis（查询直查 MySQL，量级无需缓存）。若将来引入商品缓存式的热点缓存，将按团队约束使用独立 namespace（如 `gba:{ns}:shipment:*`），不复用 auth 的 `gba:{ns}:auth:*` 前缀与序列化。
