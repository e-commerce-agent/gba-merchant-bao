package com.gba.merchantbao.shipment.webservice;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import javax.xml.namespace.QName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.w3c.dom.Element;

/**
 * Independent trust domain: server-to-server credential transported in a SOAP Header element
 * (config-injected, never shipped to browsers or the repository in plaintext). Implemented as a
 * CXF in-interceptor (PRE_PROTOCOL, after header parsing) so the credential is enforced before
 * any handler/business call. An unconfigured or mismatched token rejects every request.
 */
public class ShipmentServiceTokenInterceptor extends AbstractPhaseInterceptor<SoapMessage> {

    public static final QName TOKEN_HEADER = new QName(ShipmentWebServicePort.NAMESPACE, "serviceToken");

    private final String expectedToken;

    public ShipmentServiceTokenInterceptor(String expectedToken) {
        super(Phase.PRE_PROTOCOL);
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    public boolean configured() {
        return !expectedToken.isEmpty();
    }

    @Override
    public void handleMessage(SoapMessage message) {
        String presented = extractToken(message.getHeaders());
        if (!configured() || presented == null || !constantTimeEquals(expectedToken, presented)) {
            throw clientFault("invalid service credential");
        }
    }

    /** Extracts the first non-blank serviceToken header value; null when absent. */
    public static String extractToken(List<Header> headers) {
        for (Header header : headers) {
            if (TOKEN_HEADER.equals(header.getName()) && header.getObject() instanceof Element element) {
                String value = element.getTextContent();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private static Fault clientFault(String message) {
        // SOAP 1.1 sender fault (soap:Client) with the reason in faultstring.
        return new org.apache.cxf.binding.soap.SoapFault(message,
                new QName("http://schemas.xmlsoap.org/soap/envelope/", "Client"));
    }
}
