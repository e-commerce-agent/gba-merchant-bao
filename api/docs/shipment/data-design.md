# 物流模块数据设计（评审稿）

> 状态：物流结构现为 `V9__shipment_tables.sql`，dev 种子为 `R__070_shipment_demo_data.sql`。V1–V5 与 R010/R020/R030 冻结；V6 商品、V7 库存、V8 订单依次创建。UTC 时间、Snowflake 主键、字符串化输出与 auth 模块一致。

## 1. 表：shipment（一单一运单）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | Snowflake |
| merchant_id | BIGINT UNSIGNED | NOT NULL | 租户根；所有查询必带 |
| order_id | BIGINT UNSIGNED | NOT NULL | 关联同商户订单 `trade_order.id`；复合外键约束 `(merchant_id, order_id)` |
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
| `UNIQUE(merchant_id, id)` | 同商户轨迹复合外键的父键 |
| `UNIQUE(merchant_id, tracking_no)` | 运单号租户内唯一防重 |
| `KEY(merchant_id, order_no)` | SOAP 按商户编码+订单号查询 |

## 2. 表：shipment_track（轨迹事件，只追加）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | Snowflake |
| shipment_id | BIGINT UNSIGNED | NOT NULL | 关联同商户运单 `shipment.id`；复合外键约束 `(merchant_id, shipment_id)` |
| merchant_id | BIGINT UNSIGNED | NOT NULL | 冗余租户列，隔离查询不 join |
| event_time | DATETIME | NOT NULL | 事件时间，UTC |
| status | VARCHAR(20) | NOT NULL | 事件时状态 |
| description | VARCHAR(255) | NOT NULL | 轨迹描述（脱敏） |
| location | VARCHAR(128) | NULL | 事件地点 |

索引：

| 索引 | 服务于 |
| --- | --- |
| `KEY(merchant_id, shipment_id, event_time)` | REST/SOAP 同商户轨迹按时间升序读取 |
| `UNIQUE(merchant_id, shipment_id, event_time, description)` | 同商户运单事件防重 |

## 3. 关联与隔离决策

1. 数据库用复合外键约束运单到订单、轨迹到运单的同商户引用；Service 仍负责订单授权。外键使用限制删除语义，不级联删除履约历史。
2. **冗余 merchant_id** 到 shipment_track：轨迹隔离查询不 join 主表，SQL 一律带租户限定。
3. **冗余 order_no** 快照到 shipment：SOAP 按订单号查询免 join 订单表；订单号以下单时为准，商品/订单变更不回写。

## 4. Flyway 编号（已定稿）

- 本模块迁移为 `V9__shipment_tables.sql`，devdata 为 `R__070_shipment_demo_data.sql`。
- 统一定序：商品 V6、库存 V7、订单 V8、物流 V9；dev 种子 R040、R050、R060、R070 按相同依赖执行。V1–V5 与 R010/R020/R030 保持原摘要。
- `trade_order` 由 V8 创建，`shipment` 对其建立同商户复合外键；订单项继续保留自己的成交快照。test/prod 不加载这些 dev seeds。
- 仅支持新库及本地尚在 V5 的 dev 基线，已执行旧 V6–V9 布局的其他库不兼容。稳定运单/订单/轨迹 ID 对照及受控补种见 [dev 数据登记](../../src/main/resources/db/devdata/README.md)。

## 5. 数据分层

| 层 | 内容 | 边界 |
| --- | --- | --- |
| devdata | `R__070_shipment_demo_data.sql`：同商户订单及运单、轨迹关联 | 仅 dev 加载；仅补缺失；test/prod 不加载 |
| test fixture | `src/test/resources/db/fixture/shipment.sql`：跨租户对照、越权场景、无物流记录订单 | 测试 `@Sql` 显式加载；事务回滚或稳定标识清理 |
| 生产 | 无种子；运单数据按实际业务写入 | 本期不建物流录入后台 |

## 6. Redis

物流模块本期不使用 Redis（查询直查 MySQL，量级无需缓存）。若将来引入商品缓存式的热点缓存，将按团队约束使用独立 namespace（如 `gba:{ns}:shipment:*`），不复用 auth 的 `gba:{ns}:auth:*` 前缀与序列化。
