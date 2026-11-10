package com.gba.merchantbao.inventory.dto;

import lombok.Data;

/**
 * 商户后台库存查询返回
 */
@Data
public class InventoryVO {

    private Long skuId;

    /** 可售库存 */
    private Integer availableQty;

    /** 预占库存 */
    private Integer reservedQty;

    /** 在库总量 = 可售 + 预占 */
    private Integer totalQty;

    public static InventoryVO of(Long skuId, Integer availableQty, Integer reservedQty) {
        InventoryVO vo = new InventoryVO();
        vo.setSkuId(skuId);
        vo.setAvailableQty(availableQty);
        vo.setReservedQty(reservedQty);
        vo.setTotalQty(availableQty + reservedQty);
        return vo;
    }
}
