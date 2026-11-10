package com.gba.merchantbao.test;

import com.gba.merchantbao.shipment.webservice.ShipmentServiceTokenInterceptor;
import com.gba.merchantbao.shipment.webservice.ShipmentWebServicePort;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.MessageImpl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Credential gate: unconfigured token rejects everything; comparison is constant-time. */
class ShipmentServiceTokenInterceptorTest {

    private static Element tokenElement(String token) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().newDocument();
        Element element = document.createElementNS(ShipmentWebServicePort.NAMESPACE, "q:serviceToken");
        element.setTextContent(token);
        return element;
    }

    private static SoapMessage messageWith(Header header) {
        SoapMessage message = new SoapMessage(new MessageImpl());
        if (header != null) {
            message.getHeaders().add(header);
        }
        return message;
    }

    @Test
    void extractTokenReadsFirstNonBlankNamespacedValue() throws Exception {
        assertThat(ShipmentServiceTokenInterceptor.extractToken(
                List.of(new Header(ShipmentServiceTokenInterceptor.TOKEN_HEADER, tokenElement("good-token")))))
                .isEqualTo("good-token");
        assertThat(ShipmentServiceTokenInterceptor.extractToken(
                List.of(new Header(ShipmentServiceTokenInterceptor.TOKEN_HEADER, tokenElement("   ")))))
                .isNull();
        assertThat(ShipmentServiceTokenInterceptor.extractToken(
                List.of(new Header(new QName("other"), tokenElement("wrong-ns")))))
                .isNull();
        assertThat(ShipmentServiceTokenInterceptor.extractToken(List.of())).isNull();
    }

    @Test
    void unconfiguredCredentialRejectsEveryRequest() throws Exception {
        ShipmentServiceTokenInterceptor interceptor = new ShipmentServiceTokenInterceptor("");
        SoapMessage message = messageWith(
                new Header(ShipmentServiceTokenInterceptor.TOKEN_HEADER, tokenElement("anything")));
        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertThat(interceptor.configured()).isFalse();
    }

    @Test
    void matchingTokenPassesAndMismatchesAreRejected() throws Exception {
        ShipmentServiceTokenInterceptor interceptor = new ShipmentServiceTokenInterceptor("it-service-token");
        assertThatCode(() -> interceptor.handleMessage(messageWith(
                new Header(ShipmentServiceTokenInterceptor.TOKEN_HEADER, tokenElement("it-service-token"))))
        ).doesNotThrowAnyException();

        assertThatThrownBy(() -> interceptor.handleMessage(messageWith(
                new Header(ShipmentServiceTokenInterceptor.TOKEN_HEADER, tokenElement("wrong-token"))))
        ).isInstanceOf(Fault.class);

        SoapMessage withoutHeader = new SoapMessage(new MessageImpl());
        assertThatThrownBy(() -> interceptor.handleMessage(withoutHeader)).isInstanceOf(Fault.class);
    }

    @Test
    void headerNamespaceIsTheShipmentContractNamespace() {
        assertThat(ShipmentServiceTokenInterceptor.TOKEN_HEADER.getNamespaceURI())
                .isEqualTo(ShipmentWebServicePort.NAMESPACE);
    }
}
