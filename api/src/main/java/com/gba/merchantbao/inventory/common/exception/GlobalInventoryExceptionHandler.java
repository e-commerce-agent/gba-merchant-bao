package com.gba.merchantbao.inventory.common.exception;

import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.gba.merchantbao.inventory.common.error.InventoryErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 库存模块统一异常处理：只对 inventory.controller 生效，返回主仓 ApiResponse + 13xxx 业务码。
 * HTTP 状态按语义映射：记录不存在 404、参数 400、库存/冲突/处理中 409、其余 500。
 */
@RestControllerAdvice(basePackages = "com.gba.merchantbao.inventory.controller")
public class GlobalInventoryExceptionHandler {

    /** 业务异常：按 13xxx 错误码与语义化 HTTP 状态返回 */
    @ExceptionHandler(InventoryBizException.class)
    public ApiResponse<Void> handleBiz(InventoryBizException e, HttpServletRequest request, HttpServletResponse response) {
        return failure(request, response, statusOf(e.getCode()), e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@RequestBody 校验）：统一归到 13006 */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException e, HttpServletRequest request, HttpServletResponse response) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return failure(request, response, HttpStatus.BAD_REQUEST, InventoryErrorCode.INVALID_ARGUMENT, detail);
    }

    /** 参数校验失败（方法参数级校验） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException e, HttpServletRequest request, HttpServletResponse response) {
        return failure(request, response, HttpStatus.BAD_REQUEST, InventoryErrorCode.INVALID_ARGUMENT, e.getMessage());
    }

    /** 乐观锁冲突漏出：统一提示重试，不暴露堆栈 */
    @ExceptionHandler(VersionConflictException.class)
    public ApiResponse<Void> handleVersionConflict(HttpServletRequest request, HttpServletResponse response) {
        return failure(request, response, HttpStatus.CONFLICT, InventoryErrorCode.CONFLICT_RETRY_EXHAUSTED, "库存更新冲突，请重试");
    }

    /** 兜底：内部异常不返回堆栈细节 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(HttpServletRequest request, HttpServletResponse response) {
        return failure(request, response, HttpStatus.INTERNAL_SERVER_ERROR, InventoryErrorCode.INTERNAL_ERROR, "库存服务内部异常");
    }

    private ApiResponse<Void> failure(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, int code, String message) {
        response.setStatus(status.value());
        return new ApiResponse<>(code, message, null, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }

    private HttpStatus statusOf(int code) {
        return switch (code) {
            case InventoryErrorCode.STOCK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case InventoryErrorCode.INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
    }
}
