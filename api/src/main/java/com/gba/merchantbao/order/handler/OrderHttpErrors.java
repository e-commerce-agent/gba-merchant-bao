package com.gba.merchantbao.order.handler;

import com.gba.merchantbao.order.exception.OrderError;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class OrderHttpErrors {
    public static final String ERROR_CODE_ATTRIBUTE=OrderHttpErrors.class.getName()+".errorCode";
    private final ObjectMapper json;
    public OrderHttpErrors(ObjectMapper json){this.json=json;}
    public void write(HttpServletRequest request,HttpServletResponse response,OrderError error) throws IOException {
        request.setAttribute(ERROR_CODE_ATTRIBUTE,error.code().getCode());
        response.setStatus(error.status());response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        json.writeValue(response.getOutputStream(),ApiResponse.failure(error.code(),error.getMessage(),(String)request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
