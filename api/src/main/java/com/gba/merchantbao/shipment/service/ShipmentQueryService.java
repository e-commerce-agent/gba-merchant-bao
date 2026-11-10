package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.common.error.CommonErrorCode;
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

    public ShipmentQueryService(ShipmentMapper mapper, OrderAccessGuard orderAccess, TenantResolver tenants) {
        this.mapper = mapper;
        this.orderAccess = orderAccess;
        this.tenants = tenants;
    }

    /** REST path: tenant scope enforced by the SQL predicate; unknown order context is a unified 404. */
    public ShipmentView getForActor(Actor actor, long orderId) {
        try {
            ShipmentRow row = mapper.findByOrder(actor.principal().merchantId(), orderId);
            if (row != null) {
                return ShipmentView.of(row, mapper.findTracks(row.merchantId(), row.id()));
            }
            if (!orderAccess.resolve(actor, orderId).exists()) {
                throw new AuthError(404, CommonErrorCode.RESOURCE_NOT_FOUND);
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
            ShipmentRow row = mapper.findByOrderNo(tenant.id(), orderNo);
            if (row == null) {
                throw new AuthError(404, CommonErrorCode.RESOURCE_NOT_FOUND);
            }
            return ShipmentView.of(row, mapper.findTracks(row.merchantId(), row.id()));
        } catch (DataAccessException e) {
            throw new AuthError(500, CommonErrorCode.INTERNAL_ERROR);
        }
    }
}
