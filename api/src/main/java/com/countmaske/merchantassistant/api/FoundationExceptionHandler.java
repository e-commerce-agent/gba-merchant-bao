package com.countmaske.merchantassistant.api;

import com.countmaske.merchantassistant.common.api.ApiResponse;
import com.countmaske.merchantassistant.common.error.CommonErrorCode;
import com.countmaske.merchantassistant.infrastructure.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackageClasses = FoundationController.class)
public class FoundationExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("foundation request failed requestId={} code={} errorType={}",
                requestId, CommonErrorCode.INTERNAL_ERROR.getCode(), exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status,
                                                       CommonErrorCode errorCode,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(errorCode, errorCode.getMessage(), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
