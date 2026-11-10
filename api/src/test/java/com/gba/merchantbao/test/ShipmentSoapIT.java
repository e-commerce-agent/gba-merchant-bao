package com.gba.merchantbao.test;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOAP WebService contract (course requirement): WSDL reachable, token-gated round trip,
 * NOT_FOUND fault for unknown orders, credential rejection. /services/** is outside the
 * /api/v1/** security chain; the service token is the sole gate here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/shipment.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ShipmentSoapIT {
    static {DockerRequired.check();}

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("shipment_soap").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();if (!REDIS.isRunning()) REDIS.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("merchant.shipment.webservice.service-token", () -> "soap-it-service-token");
    }

    private static final String NS = "https://gba.merchantbao/shipment/v1";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    private ResponseEntity<String> soap(String soapAction, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/xml; charset=utf-8"));
        if (soapAction != null) headers.set("SOAPAction", "\"" + soapAction + "\"");
        var response = http.postForEntity(baseUrl() + "/services/shipment",
                new HttpEntity<>(body, headers), String.class);
        return response;
    }

    private String envelope(String token, String merchantCode, String orderNo) {
        String header = token == null ? ""
                : "<" + "q:serviceToken xmlns:q=\"" + NS + "\">" + token + "</q:serviceToken>";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:q="%s">
                  <SOAP-ENV:Header>%s</SOAP-ENV:Header>
                  <SOAP-ENV:Body>
                    <q:queryShipmentByOrderNo xmlns:q="%s">
                      <merchantCode>%s</merchantCode>
                      <orderNo>%s</orderNo>
                    </q:queryShipmentByOrderNo>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """.formatted(NS, header, NS, merchantCode, orderNo);
    }

    private Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Course-demo payload only; keep external entities off regardless.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(
                new java.io.StringReader(new String(xml.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))));
    }

    @Test
    void wsdlIsReachableWithoutCredential() {
        var response = http.getForEntity(baseUrl() + "/services/shipment?wsdl", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("queryShipmentByOrderNo");
    }

    @Test
    void missingCredentialIsRejected() {
        var response = soap("queryShipmentByOrderNo", envelope(null, "M_3C_DEMO", "FIX-0401"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Fault");
        assertThat(response.getBody()).contains("invalid service credential");
    }

    @Test
    void wrongCredentialIsRejected() {
        var response = soap("queryShipmentByOrderNo", envelope("bad-token", "M_3C_DEMO", "FIX-0401"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("invalid service credential");
    }

    @Test
    void validCredentialReturnsShipmentSummary() throws Exception {
        var response = soap("queryShipmentByOrderNo", envelope("soap-it-service-token", "M_3C_DEMO", "FIX-0401"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Document document = parse(response.getBody());
        // Wrapper children are doc/literal wrapped: namespace-agnostic lookup keeps the
        // assertions focused on payload values rather than element-form qualifiers.
        var status = document.getElementsByTagNameNS("*", "status").item(0).getTextContent();
        assertThat(status).isEqualTo("IN_TRANSIT");
        assertThat(document.getElementsByTagNameNS("*", "orderNo").item(0).getTextContent()).isEqualTo("FIX-0401");
        assertThat(document.getElementsByTagNameNS("*", "trackingNo").item(0).getTextContent()).isEqualTo("FIX1000001");
        assertThat(document.getElementsByTagNameNS("*", "track").getLength()).isEqualTo(2);
    }

    @Test
    void unknownOrderOrForeignTenantIsNotFoundFault() {
        var missing = soap("queryShipmentByOrderNo", envelope("soap-it-service-token", "M_3C_DEMO", "no-such-order"));
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(missing.getBody()).contains("NOT_FOUND");
        var foreign = soap("queryShipmentByOrderNo", envelope("soap-it-service-token", "M_ISOLATION_TEST", "FIX-0401"));
        assertThat(foreign.getBody()).contains("NOT_FOUND");
    }

    @Test
    void pingRequiresCredentialAndEchoesServiceName() {
        var rejected = soap("ping", """
                <?xml version="1.0" encoding="UTF-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                  <SOAP-ENV:Body><q:ping xmlns:q="%s"/></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """.formatted(NS));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(rejected.getBody()).contains("invalid service credential");

        var ok = soap("ping", """
                <?xml version="1.0" encoding="UTF-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:q="%s">
                  <SOAP-ENV:Header><q:serviceToken>soap-it-service-token</q:serviceToken></SOAP-ENV:Header>
                  <SOAP-ENV:Body><q:ping xmlns:q="%s"/></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """.formatted(NS, NS));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).contains("shipment WebService");
    }

    @Test
    void restSecurityChainIsUnaffectedByServicesServlet() {
        var health = http.getForEntity(baseUrl() + "/api/v1/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        var anonymousShipment = http.getForEntity(baseUrl() + "/api/v1/orders/4000000000000401/shipment", String.class);
        assertThat(anonymousShipment.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
