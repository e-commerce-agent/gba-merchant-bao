package com.gba.merchantbao.inventory.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存操作幂等记录表 inventory_idempotent
 */
@Data
public class IdempotentRecordPO {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private String idempotentKey;
    private Long merchantId;
    private String bizType;
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
