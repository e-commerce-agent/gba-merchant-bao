package com.gba.merchantbao.order.controller;

import com.gba.merchantbao.order.dto.OrderListQuery;
import com.gba.merchantbao.order.service.OrderQueryService;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.service.SessionService;

import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController("orderQueryController") @RequestMapping("/api/v1") @Tag(name="order-v1",description="Read-only order queries scoped by the trusted Actor: consumers see their own orders, staff sees the merchant's. Out-of-scope and missing resources are both 404/10004. IDs are strings and times are UTC. Errors: 400/10001, 500/10500.")
public class OrderController {
    private final OrderQueryService orders;private final SessionService sessions;
    public OrderController(OrderQueryService orders,SessionService sessions){this.orders=orders;this.sessions=sessions;}
    @GetMapping("/orders") @SecurityRequirement(name="bearerAuth") @Operation(operationId="listOrders",summary="订单分页列表",description="消费者返回本人订单，员工返回本商户订单。pageNum 默认 1，pageSize 默认 10、上限 100。")
    public ApiResponse<OrderQueryService.Page> list(@AuthenticationPrincipal Actor actor,@Valid @ModelAttribute OrderListQuery query,HttpServletRequest r){
        var result=orders.list(actor,query.page(),query.size());sessions.touch(actor);return ok(result,r);
    }
    @GetMapping("/orders/{orderId}") @SecurityRequirement(name="bearerAuth") @Operation(operationId="getOrderDetail",summary="订单详情含成交快照",description="消费者限本人订单，员工限本商户订单；越界与不存在同为 404。订单项为下单时的名称、SKU 属性与价格快照。")
    public ApiResponse<OrderQueryService.Detail> detail(@AuthenticationPrincipal Actor actor,@PathVariable long orderId,HttpServletRequest r){
        var result=orders.detail(actor,orderId);sessions.touch(actor);return ok(result,r);
    }
    private <T>ApiResponse<T> ok(T value,HttpServletRequest r){return ApiResponse.success(value,(String)r.getAttribute(RequestIdFilter.ATTRIBUTE));}
}
