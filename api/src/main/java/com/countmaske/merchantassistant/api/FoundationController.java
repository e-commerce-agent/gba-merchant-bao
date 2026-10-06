package com.countmaske.merchantassistant.api;

import com.countmaske.merchantassistant.common.api.ApiResponse;
import com.countmaske.merchantassistant.infrastructure.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "foundation")
public class FoundationController {

    @GetMapping("/health")
    @Operation(operationId = "getFoundationHealth", summary = "基础服务健康检查")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "success", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "internal server error")
    })
    @Parameter(name = RequestIdFilter.HEADER, in = ParameterIn.HEADER, description = "Optional request correlation id")
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return ApiResponse.success(Map.of("status", "UP"), requestId);
    }

    @PostMapping("/echo")
    @Operation(operationId = "echoFoundationMessage", summary = "基础请求校验样例")
    @SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "success", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "internal server error")
    })
    @Parameter(name = RequestIdFilter.HEADER, in = ParameterIn.HEADER, description = "Optional request correlation id")
    public ApiResponse<Map<String, String>> echo(@Valid @RequestBody FoundationEchoRequest body,
                                                  HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return ApiResponse.success(Map.of("message", body.message()), requestId);
    }
}
