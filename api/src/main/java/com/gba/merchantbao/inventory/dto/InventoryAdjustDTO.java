package com.gba.merchantbao.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商户后台人工调整库存请求（入库为正、盘亏为负）
 */
@Data
public class InventoryAdjustDTO {

    @NotBlank(message = "幂等键不能为空")
    private String idempotentKey;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /** 调整量，正数为增加可售库存，负数为减少，不能为 0 */
    @NotNull(message = "调整数量不能为空")
    private Integer adjustQty;

    /** 调整原因，建议必填，便于审计 */
    private String remark;
}
