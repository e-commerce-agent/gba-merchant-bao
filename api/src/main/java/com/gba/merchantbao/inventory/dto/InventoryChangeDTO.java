package com.gba.merchantbao.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存预占 / 释放 / 扣减通用请求
 * 三个动作的语义：
 *   reserve 预占：可售减少、预占增加（下单时调用）
 *   release 释放：可售增加、预占减少（取消订单时调用）
 *   deduct  扣减：预占减少（支付成功/发货后真正出库）
 */
@Data
public class InventoryChangeDTO {

    /** 幂等键，由调用方生成，建议格式：业务类型:业务单号:版本，如 DEDUCT:ORDER123:1 */
    @NotBlank(message = "幂等键不能为空")
    private String idempotentKey;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /** 变更数量，必须大于 0 */
    @NotNull(message = "变更数量不能为空")
    @Min(value = 1, message = "变更数量必须大于0")
    private Integer qty;

    /** 关联业务ID，如订单ID，用于流水追溯，可为空 */
    private Long bizId;

    /** 备注，可为空 */
    private String remark;
}
