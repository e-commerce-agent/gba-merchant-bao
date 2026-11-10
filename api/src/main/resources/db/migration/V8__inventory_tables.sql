-- Inventory module tables: SKU stock with optimistic-lock version, stock transaction ledger
-- and the idempotency table backing INSERT-based acquire.
-- Numbering: V8. The numbering shifted one place after main took V5/R030 for the
-- agent/conversation module: shipment V6, order V7, inventory V8.
-- Primary keys stay AUTO_INCREMENT for this module; ids never leave the API as numbers.

CREATE TABLE IF NOT EXISTS inventory (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id     BIGINT       NOT NULL COMMENT '租户ID，多租户隔离字段',
    sku_id          BIGINT       NOT NULL COMMENT '关联 product_sku.id',
    available_qty   INT          NOT NULL DEFAULT 0 COMMENT '可售库存（可直接售卖的数量）',
    reserved_qty    INT          NOT NULL DEFAULT 0 COMMENT '预占库存（已下单未扣减的数量）',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_sku (merchant_id, sku_id),
    KEY idx_sku (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SKU库存表';

CREATE TABLE IF NOT EXISTS inventory_transaction (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id      BIGINT       NOT NULL COMMENT '租户ID',
    sku_id           BIGINT       NOT NULL COMMENT '关联 product_sku.id',
    biz_type         VARCHAR(32)  NOT NULL COMMENT '业务类型：ADJUST/RESERVE/RELEASE/DEDUCT',
    biz_id           BIGINT       NULL COMMENT '关联业务ID（如订单ID）',
    change_available INT          NOT NULL COMMENT '可售库存变化量，正增负减',
    change_reserved  INT          NOT NULL COMMENT '预占库存变化量，正增负减',
    before_available INT          NOT NULL COMMENT '变更前可售',
    after_available  INT          NOT NULL COMMENT '变更后可售',
    before_reserved  INT          NOT NULL COMMENT '变更前预占',
    after_reserved   INT          NOT NULL COMMENT '变更后预占',
    idempotent_key   VARCHAR(128) NULL COMMENT '幂等键，便于对账追溯',
    operator_id      VARCHAR(64)  NULL COMMENT '操作人（商户员工ID或系统标识）',
    remark           VARCHAR(255) NULL COMMENT '备注',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水时间',
    PRIMARY KEY (id),
    KEY idx_merchant_sku_time (merchant_id, sku_id, create_time),
    KEY idx_idempotent_key (idempotent_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存变动流水表';

CREATE TABLE IF NOT EXISTS inventory_idempotent (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等唯一键',
    merchant_id    BIGINT       NOT NULL COMMENT '租户ID',
    biz_type       VARCHAR(32)  NOT NULL COMMENT '业务类型：ADJUST/RESERVE/RELEASE/DEDUCT',
    status         VARCHAR(16)  NOT NULL COMMENT '处理状态：PROCESSING/SUCCESS/FAILED',
    error_msg      VARCHAR(255) NULL COMMENT '失败原因摘要',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotent_key (idempotent_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存操作幂等记录表';
