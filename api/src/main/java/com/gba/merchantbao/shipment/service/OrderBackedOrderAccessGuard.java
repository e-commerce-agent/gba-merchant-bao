package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.order.mapper.OrderQueryMapper;

import org.springframework.stereotype.Component;

/**
 * Real order-module implementation of the authorization port: ownership is resolved from the
 * trusted Actor against the order tables — consumers only for their own orders, staff only
 * inside their merchant. Request-supplied ids are locating parameters, never authority.
 * An existing order without shipment rows yields exists=true so the service synthesizes
 * 200/NOT_SHIPPED; missing/foreign orders yield exists=false and stay a unified 404.
 */
@Component
public class OrderBackedOrderAccessGuard implements OrderAccessGuard {

    private final OrderQueryMapper orders;

    public OrderBackedOrderAccessGuard(OrderQueryMapper orders) {
        this.orders = orders;
    }

    @Override
    public OrderAccess resolve(Actor actor, long orderId) {
        var principal = actor.principal();
        boolean exists;
        if (principal.role() == AccountRole.CONSUMER) {
            exists = orders.findOwnedById(principal.merchantId(), orderId, principal.accountId()) != null;
        } else if (principal.role() == AccountRole.MERCHANT_STAFF) {
            exists = orders.findById(principal.merchantId(), orderId) != null;
        } else {
            exists = false;
        }
        return new OrderAccess(exists, null);
    }
}
