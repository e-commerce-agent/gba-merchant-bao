package com.gba.merchantbao.shipment.config;

import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class ShipmentOpenApiConfiguration {

    @Bean
    GroupedOpenApi shipmentV1OpenApi() {
        return GroupedOpenApi.builder().group("shipment-v1")
            .packagesToScan("com.gba.merchantbao.shipment.controller")
            .pathsToMatch("/api/v1/orders/*/shipment").addOpenApiCustomizer(api -> {
                api.getInfo().setTitle("Merchant Shipment API");
                api.getInfo().setDescription("物流契约评审稿实现快照：1 只读操作。SOAP WebService（/services/shipment?wsdl）为独立信任域，"
                        + "不进入本文档。错误复用公共段，12xxx 预留。IDs 字符串化、时间 UTC、所有响应 no-store。");
                api.setServers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
                Schema<?> envelope = new ObjectSchema().addProperty("code", new IntegerSchema())
                    .addProperty("message", new StringSchema())
                    .addProperty("data", new Schema<>().nullable(true))
                    .addProperty("requestId", new StringSchema());
                api.getComponents().addSchemas("ShipmentErrorEnvelope", envelope);
                Map<String, String> errors = Map.of("400", "10001 参数无效", "401", "10002 会话无效",
                    "403", "10003 角色越权", "404", "10004 订单不存在/跨租户/非本人（不可区分）",
                    "500", "10500 非预期错误");
                api.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                    errors.forEach((status, description) -> op.getResponses().addApiResponse(status,
                        new ApiResponse().description(description)
                            .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ShipmentErrorEnvelope"))))));
                    op.getResponses().forEach((status, response) -> {
                        response.addHeaderObject("X-Request-Id", new Header().description("与 envelope.requestId 相同").schema(new StringSchema()));
                        response.addHeaderObject("Cache-Control", new Header().schema(new StringSchema()._const("no-store")));
                    });
                }));
            }).build();
    }
}
