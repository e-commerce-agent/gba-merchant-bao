package com.gba.merchantbao.inventory.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SKU 库存表 inventory
 */
@Data
public class InventoryPO {

    private Long id;
    private Long merchantId;
    private Long skuId;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
