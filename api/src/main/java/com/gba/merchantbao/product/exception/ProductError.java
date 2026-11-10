package com.gba.merchantbao.product.exception;

import com.gba.merchantbao.common.error.CommonErrorCode;
import lombok.Getter;

@Getter
public final class ProductError extends RuntimeException {
    private final int status;
    private final CommonErrorCode code;
    public ProductError(int status, CommonErrorCode code) { super(code.getMessage()); this.status = status; this.code = code; }
    public static ProductError invalid() { return new ProductError(400, CommonErrorCode.INVALID_ARGUMENT); }
    public static ProductError missing() { return new ProductError(404, CommonErrorCode.RESOURCE_NOT_FOUND); }
    public static ProductError forbidden() { return new ProductError(403, CommonErrorCode.FORBIDDEN); }
    public static ProductError conflict() { return new ProductError(409, CommonErrorCode.CONFLICT); }
}
