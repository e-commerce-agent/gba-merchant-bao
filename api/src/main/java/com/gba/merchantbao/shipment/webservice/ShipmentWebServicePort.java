package com.gba.merchantbao.shipment.webservice;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

/**
 * 课程硬性要求「WebService（分布式框架初识）」的落点契约。SOAP/JAX-WS（Apache CXF，Jakarta），
 * WSDL 通过 HTTP GET /services/shipment?wsdl 访问。独立信任域：服务间凭证随 SOAP Header 传递，
 * 不接受浏览器 Bearer 会话，不提供绕过 REST 授权的消费者级查询；与 REST 复用同一个
 * ShipmentQueryService，脱敏规则同源。定位为课程演示 + 模拟外部承运商系统对接，
 * 不开放为生产对公能力。
 */
@WebService(name = "ShipmentWebServicePort", targetNamespace = ShipmentWebServicePort.NAMESPACE)
public interface ShipmentWebServicePort {

    String NAMESPACE = "https://gba.merchantbao/shipment/v1";

    /** Connectivity probe for the course demo; no database access. */
    @WebMethod(operationName = "ping", action = "ping")
    String ping();

    /**
     * Read-only shipment summary by merchant code + order number.
     * Missing tenant/order or a foreign order is an indistinguishable NOT_FOUND fault.
     */
    @WebMethod(operationName = "queryShipmentByOrderNo", action = "queryShipmentByOrderNo")
    ShipmentSummary queryShipmentByOrderNo(
            @WebParam(name = "merchantCode") String merchantCode,
            @WebParam(name = "orderNo") String orderNo) throws ShipmentWebServiceFault;
}
