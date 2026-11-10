package com.gba.merchantbao.inventory.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存变动流水表 inventory_transaction
 */
@Data
public class InventoryTransactionPO {

    private Long id;
    private Long merchantId;
    private Long skuId;
    private String bizType;
    private Long bizId;
    private Integer changeAvailable;
    private Integer changeReserved;
    private Integer beforeAvailable;
    private Integer afterAvailable;
    private Integer beforeReserved;
    private Integer afterReserved;
    private String idempotentKey;
    private String operatorId;
    private String remark;
    private LocalDateTime createTime;
}
