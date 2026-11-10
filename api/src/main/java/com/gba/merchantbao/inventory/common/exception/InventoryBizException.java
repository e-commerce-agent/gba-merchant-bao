package com.gba.merchantbao.inventory.common.exception;

import lombok.Getter;

/**
 * 库存业务异常，携带 13xxx 错误码
 */
@Getter
public class InventoryBizException extends RuntimeException {

    private final int code;

    public InventoryBizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
