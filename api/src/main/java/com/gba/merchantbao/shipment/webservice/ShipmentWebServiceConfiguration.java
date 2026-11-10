package com.gba.merchantbao.shipment.webservice;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFServlet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the SOAP endpoint. CXFServlet is mapped at /services/* (cxf.path, default /services),
 * so this endpoint resolves to /services/shipment with its WSDL at /services/shipment?wsdl.
 * The bean name cxfServletRegistration intentionally matches CXF's CxfAutoConfiguration
 * (@ConditionalOnMissingBean(name = "cxfServletRegistration")) so the servlet is registered
 * exactly once. /services/** is outside the /api/v1/** security chain; the service token
 * interceptor is the sole gate for this trust domain. /services/** is excluded from Springdoc
 * scanning, so the SOAP contract never mixes into the REST OpenAPI document.
 */
@Configuration(proxyBeanMethods = false)
public class ShipmentWebServiceConfiguration {

    @Bean(name = "cxfServletRegistration")
    public ServletRegistrationBean<CXFServlet> shipmentCxfServlet() {
        return new ServletRegistrationBean<>(new CXFServlet(), "/services/*");
    }

    @Bean(destroyMethod = "")
    public org.apache.cxf.jaxws.EndpointImpl shipmentEndpoint(Bus bus, ShipmentWebService service,
            ShipmentServiceTokenInterceptor tokenInterceptor) {
        // Lifecycle is tied to the CXF Bus, which shuts down with the application context;
        // destroyMethod="" disables Spring's inferred close()/destroy() detection.
        EndpointImpl endpoint = new EndpointImpl(bus, service);
        endpoint.getInInterceptors().add(tokenInterceptor);
        endpoint.publish("/shipment");
        return endpoint;
    }

    @Bean
    public ShipmentServiceTokenInterceptor shipmentServiceTokenInterceptor(
            @Value("${merchant.shipment.webservice.service-token:}") String serviceToken) {
        return new ShipmentServiceTokenInterceptor(serviceToken);
    }
}
