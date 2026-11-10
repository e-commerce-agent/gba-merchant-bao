package com.gba.merchantbao.order.handler;

import com.gba.merchantbao.order.exception.OrderError;

import com.gba.merchantbao.common.error.CommonErrorCode;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestControllerAdvice(basePackages="com.gba.merchantbao.order.controller")
public class OrderExceptionHandler {
    private final OrderHttpErrors errors;
    public OrderExceptionHandler(OrderHttpErrors errors){this.errors=errors;}
    @ExceptionHandler(Exception.class)
    public void failure(Exception e,HttpServletRequest request,HttpServletResponse response) throws IOException {
        OrderError result;
        if(e instanceof OrderError o)result=o;
        else if(e instanceof IllegalArgumentException||e instanceof org.springframework.http.converter.HttpMessageNotReadableException||e instanceof org.springframework.web.bind.MethodArgumentNotValidException||e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException)result=new OrderError(400,CommonErrorCode.INVALID_ARGUMENT);
        else if(e instanceof org.springframework.dao.DataAccessException)result=new OrderError(500,CommonErrorCode.INTERNAL_ERROR);
        else result=new OrderError(500,CommonErrorCode.INTERNAL_ERROR);
        errors.write(request,response,result);
    }
}
