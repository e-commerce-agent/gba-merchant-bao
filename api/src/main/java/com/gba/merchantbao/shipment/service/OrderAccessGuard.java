package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.security.Actor;

/**
 * Port to the order module's ownership query. The real implementation arrives with the order
 * module and MUST resolve ownership from trusted data: consumers only for their own orders,
 * staff only inside their merchant. Request-supplied ids are locating parameters, never authority.
 */
public interface OrderAccessGuard {

    /** @return exists=false when the order is missing, foreign or simply not resolvable yet. */
    OrderAccess resolve(Actor actor, long orderId);

    record OrderAccess(boolean exists, String orderNo) {
    }
}
