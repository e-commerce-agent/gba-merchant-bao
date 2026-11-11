package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.security.Actor;

/**
 * Adapter port to the order module's authorization service: consumers only for their own orders,
 * staff only inside their merchant. Request-supplied ids are locating parameters, never authority.
 */
public interface OrderAccessGuard {

    /** Unauthorized or missing orders fail with the unified 404 before shipment reads. */
    OrderAccess resolve(Actor actor, long orderId);

    record OrderAccess(boolean exists, String orderNo) {
    }
}
