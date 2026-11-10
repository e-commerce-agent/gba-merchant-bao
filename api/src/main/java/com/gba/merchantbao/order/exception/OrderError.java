package com.gba.merchantbao.order.exception;

import com.gba.merchantbao.common.error.CommonErrorCode;

/** Stable public failures; never attach storage or dependency exception text. */
public final class OrderError extends RuntimeException {
    private final int status;
    private final CommonErrorCode code;

    public OrderError(int status, CommonErrorCode code) {
        super(code.getMessage());
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public CommonErrorCode code() { return code; }

    public static OrderError notFound() { return new OrderError(404, CommonErrorCode.RESOURCE_NOT_FOUND); }
}
