package com.countmaske.merchantassistant.common.api;

import com.countmaske.merchantassistant.common.error.CommonErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Versioned API response envelope. Legacy /admin and /user endpoints keep their existing envelope.
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private static final int SUCCESS_CODE = 0;

    private final int code;
    private final String message;
    private final T data;
    private final String requestId;

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(SUCCESS_CODE, "success", data, requestId);
    }

    public static <T> ApiResponse<T> failure(CommonErrorCode errorCode, String message, String requestId) {
        return new ApiResponse<>(errorCode.getCode(), message == null ? errorCode.getMessage() : message, null, requestId);
    }
}
