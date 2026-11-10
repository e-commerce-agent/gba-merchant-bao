package com.gba.merchantbao.shipment.webservice;

import jakarta.xml.ws.WebFault;

/**
 * Standard JAX-WS mapped fault: serialized as a SOAP Fault with faultstring = message and a
 * detail element {@code <ns:ShipmentFault><code>…</code></ns:ShipmentFault>}. NOT_FOUND is
 * deliberately indistinguishable between "no such tenant/order" and "foreign tenant order".
 */
@WebFault(name = "ShipmentFault", targetNamespace = ShipmentWebServicePort.NAMESPACE)
public class ShipmentWebServiceFault extends Exception {

    private final String code;

    public ShipmentWebServiceFault(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getFaultInfo() {
        return code;
    }
}
