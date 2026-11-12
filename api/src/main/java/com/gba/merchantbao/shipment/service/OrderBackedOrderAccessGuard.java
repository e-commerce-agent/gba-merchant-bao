package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.order.exception.OrderError;
import com.gba.merchantbao.order.service.OrderQueryService;

import org.springframework.stereotype.Component;

/**
 * Real order-module implementation of the authorization port: ownership is resolved from the
 * trusted Actor against the order tables — consumers only for their own orders, staff only
 * inside their merchant. Request-supplied ids are locating parameters, never authority.
 * Delegates authorization to the order service before shipment data can be read.
 * Missing/foreign orders stay a unified 404; no order persistence types cross this adapter.
 */
@Component
public class OrderBackedOrderAccessGuard implements OrderAccessGuard {

    private final OrderQueryService orders;

    public OrderBackedOrderAccessGuard(OrderQueryService orders) {
        this.orders = orders;
    }

    @Override
    public OrderAccess resolve(Actor actor, long orderId) {
        try {
            var order = orders.locateForActor(actor, orderId);
            return new OrderAccess(true, order.orderNo());
        } catch (OrderError error) {
            throw new AuthError(error.status(), error.code());
        }
    }
}
