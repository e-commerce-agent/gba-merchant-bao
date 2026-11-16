package com.gba.merchantbao.knowledge.exception;

import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.gba.merchantbao.knowledge.controller")
public class KnowledgeExceptionHandler {
    @ExceptionHandler(KnowledgeError.class)
    ResponseEntity<ApiResponse<Void>> knowledge(KnowledgeError error, HttpServletRequest request) {
        return body(error.status(), error.code(), request);
    }
    @ExceptionHandler(AuthError.class)
    ResponseEntity<ApiResponse<Void>> auth(AuthError error, HttpServletRequest request) {
        return body(error.status(), error.code(), request);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> oversized(Exception error, HttpServletRequest request) {
        return body(413, CommonErrorCode.INVALID_ARGUMENT, request);
    }
    @ExceptionHandler({IllegalArgumentException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class, MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception error, HttpServletRequest request) {
        return body(400, CommonErrorCode.INVALID_ARGUMENT, request);
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> unsupported(Exception error, HttpServletRequest request) {
        return body(415, CommonErrorCode.INVALID_ARGUMENT, request);
    }
    @ExceptionHandler({DataAccessException.class, org.springframework.transaction.TransactionException.class})
    ResponseEntity<ApiResponse<Void>> storage(Exception error, HttpServletRequest request) {
        return body(503, CommonErrorCode.DEPENDENCY_ERROR, request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request) {
        return body(500, CommonErrorCode.INTERNAL_ERROR, request);
    }
    private ResponseEntity<ApiResponse<Void>> body(int status, CommonErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(ApiResponse.failure(code, code.getMessage(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
