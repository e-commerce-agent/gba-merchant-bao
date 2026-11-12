package com.gba.merchantbao.inventory.config;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** Export the existing staff-only inventory contract alongside the other commerce groups. */
@Configuration(proxyBeanMethods = false)
public class InventoryOpenApiConfiguration {
    @Bean
    GroupedOpenApi inventoryV1OpenApi() {
        return GroupedOpenApi.builder().group("inventory-v1")
                .packagesToScan("com.gba.merchantbao.inventory.controller")
                .pathsToMatch("/api/v1/inventory/**").addOpenApiCustomizer(api -> {
                    api.getInfo().setTitle("Merchant Inventory API");
                    api.getInfo().setDescription("库存员工维护与精确查询。全部接口需要有效 Bearer 及 MERCHANT_STAFF；"
                            + "商户与操作人只来自可信 Actor。消费者返回 400/13006，匿名返回 401。"
                            + "ACTIVE SKU 可在商品发布前备货，首次正向 adjust 从零初始化；停售 SKU 禁止新增供应/预占，允许已有库存收尾。"
                            + "幂等成功重放不重复写入，内部主键由服务端雪花生成，不接受客户端指定。"
                            + "公开库存布尔由商品接口提供；这些维护操作不自动创建或推进订单。");
                    api.setServers(List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
                    Schema<?> error = new ObjectSchema().addProperty("code", new IntegerSchema())
                            .addProperty("message", new StringSchema()).addProperty("data", new Schema<>().nullable(true))
                            .addProperty("requestId", new StringSchema());
                    api.getComponents().addSchemas("InventoryErrorEnvelope", error);
                    Map<String, String> errors = Map.of("400", "13006 参数或员工角色不合法（通用参数校验可为 10001）",
                            "401", "10002 会话无效", "404", "13001 本商户 SKU/库存不存在，跨商户相同",
                            "409", "13002 可售不足 / 13003 重试耗尽 / 13004 处理中 / 13005 预占不足 / 13007 盘亏低于零 / 13008 SKU 停售",
                            "500", "13999 库存内部/存储异常");
                    api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                        errors.forEach((status, description) -> operation.getResponses().addApiResponse(status,
                                new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/InventoryErrorEnvelope"))))));
                    }));
                }).build();
    }
}
