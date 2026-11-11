package com.gba.merchantbao.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Springdoc group for the product module; wire details live on controllers and DTOs. */
@Configuration(proxyBeanMethods = false)
public class ProductOpenApiConfiguration {
    @Bean
    GroupedOpenApi productV1OpenApi() {
        return GroupedOpenApi.builder()
                .group("product-v1")
                .packagesToScan("com.gba.merchantbao.product.controller")
                .addOpenApiCustomizer(api -> {
                    Info info = new Info();
                    info.setTitle("Product module API");
                    info.setVersion("v1");
                    info.setDescription("""
                            公开商城与商户商品目录。公开接口匿名访问，店铺编码解析 ACTIVE 商户，只返回 PUBLISHED 商品与 ACTIVE SKU；
                            公开列表/详情内的 SKU 及 SKU 详情包含 inStock 布尔，当前可售库存大于零为 true，缺少库存行或可售为零为 false。
                            目录缓存不保存库存状态，每次请求组合当前库存；公开响应不暴露精确数量、预占量或流水，精确库存仅员工可读。
                            登录读取按角色过滤可见性，写操作仅限商户员工，身份一律来自服务端 Actor。
                            ID 为十进制字符串，时间为 UTC ISO-8601，所有响应 no-store。错误码：10001 参数无效、10002 未认证、10003 越权、10004 不存在/跨商户统一不可见、10009 冲突、10500 内部错误。""");
                    api.setInfo(info);
                })
                .build();
    }
}
