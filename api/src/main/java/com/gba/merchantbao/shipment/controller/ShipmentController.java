package com.gba.merchantbao.shipment.controller;

import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.gba.merchantbao.shipment.dto.ShipmentView;
import com.gba.merchantbao.shipment.service.ShipmentQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "shipment-v1", description = "Read-only shipment query; Bearer access required. Errors reuse the common "
        + "segments: 400/10001, 401/10002, 403/10003, 404/10004 (missing, cross-tenant and foreign orders are "
        + "indistinguishable), 500/10500. IDs are strings and times are UTC. Tracking numbers are never a lookup key.")
public class ShipmentController {

    private final ShipmentQueryService shipments;

    public ShipmentController(ShipmentQueryService shipments) {
        this.shipments = shipments;
    }

    @GetMapping("/orders/{orderId}/shipment")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "getOrderShipment", summary = "按订单查询物流",
            description = "消费者限本人订单，员工限本商户订单；归属由服务端按可信 Actor 校验。订单不存在、跨租户、"
                    + "非本人统一 404/10004。无物流记录时（订单模块就绪后）返回 status=NOT_SHIPPED 且空轨迹。"
                    + "estimatedDeliveryAt 仅数据提供时返回。")
    public ApiResponse<ShipmentView> get(@AuthenticationPrincipal Actor actor,
            @PathVariable String orderId, HttpServletRequest request) {
        return ApiResponse.success(shipments.getForActor(actor, parseOrderId(orderId)),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }

    private static long parseOrderId(String orderId) {
        try {
            long value = Long.parseLong(orderId);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid orderId");
        }
    }
}
