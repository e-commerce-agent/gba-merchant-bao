package com.gba.merchantbao.shipment.webservice;

import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.shipment.dto.ShipmentView;
import com.gba.merchantbao.shipment.service.ShipmentQueryService;

import jakarta.jws.WebService;

/**
 * SOAP endpoint implementation published at /services/shipment (WSDL: /services/shipment?wsdl).
 * Credential validation is enforced by ShipmentServiceTokenInterceptor before any business call;
 * data comes from the same ShipmentQueryService as REST. Faults use the standard JAX-WS
 * {@link ShipmentWebServiceFault} mapping (no SAAJ dependency).
 */
@WebService(serviceName = "ShipmentWebService", portName = "ShipmentWebServicePort",
        targetNamespace = ShipmentWebServicePort.NAMESPACE,
        endpointInterface = "com.gba.merchantbao.shipment.webservice.ShipmentWebServicePort")
@org.springframework.stereotype.Service
public class ShipmentWebService implements ShipmentWebServicePort {

    private final ShipmentQueryService shipments;

    public ShipmentWebService(ShipmentQueryService shipments) {
        this.shipments = shipments;
    }

    @Override
    public String ping() {
        return "gba-merchant-bao shipment WebService v1";
    }

    @Override
    public ShipmentSummary queryShipmentByOrderNo(String merchantCode, String orderNo) throws ShipmentWebServiceFault {
        if (merchantCode == null || merchantCode.isBlank() || orderNo == null || orderNo.isBlank()) {
            throw new ShipmentWebServiceFault("INVALID_ARGUMENT", "invalid argument");
        }
        try {
            ShipmentView view = shipments.getForMerchantCode(merchantCode.trim(), orderNo.trim());
            return ShipmentSummary.from(view);
        } catch (AuthError e) {
            if (e.status() == 404) {
                throw new ShipmentWebServiceFault("NOT_FOUND", "NOT_FOUND");
            }
            throw new IllegalStateException("internal server error");
        }
    }
}
