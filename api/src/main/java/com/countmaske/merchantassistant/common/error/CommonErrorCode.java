package com.countmaske.merchantassistant.common.error;

import lombok.Getter;

@Getter
public enum CommonErrorCode {
    INVALID_ARGUMENT(10001, "invalid argument"),
    UNAUTHORIZED(10002, "unauthorized"),
    FORBIDDEN(10003, "forbidden"),
    RESOURCE_NOT_FOUND(10004, "resource not found"),
    CONFLICT(10009, "conflict"),
    INTERNAL_ERROR(10500, "internal server error");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
