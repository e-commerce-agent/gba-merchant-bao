package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.order.exception.OrderError;
import com.gba.merchantbao.order.service.OrderQueryService;
import com.gba.merchantbao.shipment.dto.ShipmentView;
import com.gba.merchantbao.shipment.entity.ShipmentRow;
import com.gba.merchantbao.shipment.mapper.ShipmentMapper;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Single read-only query implementation shared by the REST path (trusted Actor) and the SOAP
 * WebService path (service-client trust domain). Same desensitized data source for both.
 */
@Service
public class ShipmentQueryService {

    private final ShipmentMapper mapper;
    private final OrderAccessGuard orderAccess;
    private final TenantResolver tenants;
    private final OrderQueryService orders;

    public ShipmentQueryService(ShipmentMapper mapper, OrderAccessGuard orderAccess, TenantResolver tenants,
                                OrderQueryService orders) {
        this.mapper = mapper;
        this.orderAccess = orderAccess;
        this.tenants = tenants;
        this.orders = orders;
    }

    /** REST path: authorize the real order before reading any shipment or track rows. */
    public ShipmentView getForActor(Actor actor, long orderId) {
        try {
            if (!orderAccess.resolve(actor, orderId).exists()) {
                throw new AuthError(404, CommonErrorCode.RESOURCE_NOT_FOUND);
            }
            ShipmentRow row = mapper.findByOrder(actor.principal().merchantId(), orderId);
            if (row != null) {
                return ShipmentView.of(row, mapper.findTracks(row.merchantId(), row.id()));
            }
            return ShipmentView.notShipped(orderId);
        } catch (DataAccessException e) {
            throw new AuthError(500, CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * SOAP path: merchant resolved from merchantCode (never from request-injected ids).
     * Missing tenant, missing order_no or a foreign order are indistinguishable 404s.
     */
    public ShipmentView getForMerchantCode(String merchantCode, String orderNo) {
        try {
            Tenant tenant = tenants.resolveActive(merchantCode)
                    .orElseThrow(() -> new AuthError(404, CommonErrorCode.RESOURCE_NOT_FOUND));
            var order = orders.locateForService(tenant.id(), orderNo);
            ShipmentRow row = mapper.findByOrder(order.merchantId(), order.orderId());
            if (row == null) {
                return ShipmentView.notShipped(order.orderId(), order.orderNo());
            }
            return ShipmentView.of(row, mapper.findTracks(row.merchantId(), row.id()));
        } catch (OrderError e) {
            throw new AuthError(e.status(), e.code());
        } catch (DataAccessException e) {
            throw new AuthError(500, CommonErrorCode.INTERNAL_ERROR);
        }
    }
}
