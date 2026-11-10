package com.gba.merchantbao.order.config;

import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;
import java.util.Map;

@Configuration(proxyBeanMethods=false)
public class OrderOpenApiConfiguration {
    @Bean GroupedOpenApi orderV1OpenApi() {
        return GroupedOpenApi.builder().group("order-v1").packagesToScan("com.gba.merchantbao.order.controller")
            .pathsToMatch("/api/v1/orders/**").addOpenApiCustomizer(api->{
                api.getInfo().setDescription("订单只读查询：消费者本人订单、员工本商户订单。身份只来自认证 Actor；越界与不存在同为 404/10004。订单项为成交快照，不含手机号与地址。");
                api.setServers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
                Schema<?> envelope=new ObjectSchema().addProperty("code",new IntegerSchema()).addProperty("message",new StringSchema())
                    .addProperty("data",new Schema<>().nullable(true)).addProperty("requestId",new StringSchema());
                api.getComponents().addSchemas("OrderErrorEnvelope",envelope);
                Map<String,String> errors=Map.of("400","10001 分页参数无效", "401","10002 会话无效",
                    "404","10004 订单不存在或越界", "500","10500 非预期错误");
                api.getPaths().values().forEach(path->path.readOperations().forEach(op->{
                    errors.forEach((status,description)->op.getResponses().addApiResponse(status,new ApiResponse().description(description)
                        .content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/OrderErrorEnvelope"))))));
                    op.getResponses().forEach((status,response)->response.addHeaderObject("X-Request-Id",new Header().description("与 envelope.requestId 相同").schema(new StringSchema())));
                }));
            }).build();
    }
}
