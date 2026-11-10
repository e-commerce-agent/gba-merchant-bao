package com.gba.merchantbao.shipment.handler;

import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.handler.AuthHttpErrors;

import com.gba.merchantbao.common.error.CommonErrorCode;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

/** Same error contract as auth: stable codes, no-store, requestId echo; shipment adds no own codes. */
@RestControllerAdvice(basePackages = "com.gba.merchantbao.shipment.controller")
public class ShipmentExceptionHandler {

    private final AuthHttpErrors errors;

    public ShipmentExceptionHandler(AuthHttpErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(Exception.class)
    public void failure(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthError result;
        if (e instanceof AuthError a) {
            result = a;
        } else if (e instanceof IllegalArgumentException
                || e instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {
            result = new AuthError(400, CommonErrorCode.INVALID_ARGUMENT);
        } else {
            result = new AuthError(500, CommonErrorCode.INTERNAL_ERROR);
        }
        errors.write(request, response, result);
    }
}
