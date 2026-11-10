package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.SessionService;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP contract for GET /api/v1/orders/{orderId}/shipment with the real order-module
 * authorization: ownership comes from the orders tables via OrderBackedOrderAccessGuard —
 * an existing owned order without shipment rows is 200/NOT_SHIPPED, while missing/foreign
 * orders stay an indistinguishable 404/10004.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/orders.sql", "/db/fixture/shipment.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ShipmentFlowIT {
    static {DockerRequired.check();}

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("shipment_flow").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource static void connections(org.springframework.test.context.DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();if (!REDIS.isRunning()) REDIS.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final long DEMO_TENANT = 1000000000000001L;
    private static final long ISOLATION_TENANT = 1000000000000002L;
    private static final long IN_TRANSIT_ORDER = 4000000000000401L;
    private static final long DELIVERED_ORDER = 4000000000000402L;
    private static final long ISOLATION_ORDER = 4000000000000403L;

    @Autowired SessionService sessions;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    private AuthPrincipal staff(long merchantId, long accountId) {
        return new AuthPrincipal(accountId, merchantId, AccountRole.MERCHANT_STAFF);
    }

    private AuthPrincipal consumer(long merchantId, long accountId) {
        return new AuthPrincipal(accountId, merchantId, AccountRole.CONSUMER);
    }

    private ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", "shipment-it-correlation");
        if (token != null) headers.setBearerAuth(token);
        var response = http.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
        if (response.getHeaders().getFirst("X-Request-Id") != null) {
            assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("shipment-it-correlation");
        }
        return response;
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).path("data");
    }

    @Test
    void staffReadsOwnTenantShipmentWithTracksAndStringIds() throws Exception {
        String token = sessions.login(staff(DEMO_TENANT, 2000000000000101L)).accessToken();
        var response = get("/api/v1/orders/" + IN_TRANSIT_ORDER + "/shipment", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        var view = data(response);
        assertThat(view.path("orderId").asText()).isEqualTo(String.valueOf(IN_TRANSIT_ORDER));
        assertThat(view.path("orderId").isTextual()).isTrue();
        assertThat(view.path("orderNo").asText()).isEqualTo("FIX-0401");
        assertThat(view.path("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(view.path("carrierName").asText()).isEqualTo("顺丰速运");
        assertThat(view.path("trackingNo").asText()).isEqualTo("FIX1000001");
        assertThat(view.path("estimatedDeliveryAt").isTextual()).isTrue();
        assertThat(view.path("tracks").size()).isEqualTo(2);
        var first = view.path("tracks").get(0);
        assertThat(first.path("eventTime").isTextual()).isTrue();
        assertThat(first.path("status").asText()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void deliveredShipmentCarriesDeliveredAtAndNoSynthesizedEstimate() throws Exception {
        String token = sessions.login(staff(DEMO_TENANT, 2000000000000101L)).accessToken();
        var view = data(get("/api/v1/orders/" + DELIVERED_ORDER + "/shipment", token));
        assertThat(view.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(view.path("deliveredAt").isTextual()).isTrue();
        assertThat(view.has("estimatedDeliveryAt")).isFalse();
    }

    @Test
    void consumerOfSameTenantMayQueryInIntegrationState() throws Exception {
        String token = sessions.login(consumer(DEMO_TENANT, 2000000000000201L)).accessToken();
        var response = get("/api/v1/orders/" + IN_TRANSIT_ORDER + "/shipment", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).path("status").asText()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void crossTenantIsIndistinguishableFromMissing() throws Exception {
        String isolationStaff = sessions.login(staff(ISOLATION_TENANT, 2000000000000102L)).accessToken();
        var foreign = get("/api/v1/orders/" + IN_TRANSIT_ORDER + "/shipment", isolationStaff);
        var missing = get("/api/v1/orders/9999999999999999/shipment", isolationStaff);
        for (var response : new ResponseEntity<?>[]{foreign, missing}) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(json.readTree(foreign.getBody()).path("code").asInt()).isEqualTo(10004);
        assertThat(json.readTree(missing.getBody()).path("code").asInt()).isEqualTo(10004);
    }

    @Test
    void anonymousIs401AndInvalidOrderIdIs400() throws Exception {
        var anonymous = get("/api/v1/orders/" + IN_TRANSIT_ORDER + "/shipment", null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(anonymous.getBody()).path("code").asInt()).isIn(10002);

        String token = sessions.login(staff(DEMO_TENANT, 2000000000000101L)).accessToken();
        var invalid = get("/api/v1/orders/not-a-number/shipment", token);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(invalid.getBody()).path("code").asInt()).isEqualTo(10001);
    }

    @Test
    void ownedOrderWithoutShipmentRowSynthesizesNotShippedAndForeignStays404() throws Exception {
        // orders.sql: 2000000000000301 belongs to consumer 2000000000000201 in M_3C_DEMO and has no shipment row.
        String staff = sessions.login(staff(DEMO_TENANT, 2000000000000101L)).accessToken();
        var response = get("/api/v1/orders/2000000000000301/shipment", staff);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var view = data(response);
        assertThat(view.path("status").asText()).isEqualTo("NOT_SHIPPED");
        assertThat(view.path("orderId").asText()).isEqualTo("2000000000000301");
        assertThat(view.path("orderId").isTextual()).isTrue();
        assertThat(view.path("tracks").size()).isZero();

        // A consumer who does not own the order and a foreign-tenant staff both get the unified 404.
        String otherConsumer = sessions.login(consumer(DEMO_TENANT, 2000000000000202L)).accessToken();
        var foreignConsumer = get("/api/v1/orders/2000000000000301/shipment", otherConsumer);
        assertThat(foreignConsumer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(foreignConsumer.getBody()).path("code").asInt()).isEqualTo(10004);

        String isolationStaff = sessions.login(staff(ISOLATION_TENANT, 2000000000000102L)).accessToken();
        var foreignStaff = get("/api/v1/orders/2000000000000301/shipment", isolationStaff);
        assertThat(foreignStaff.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(foreignStaff.getBody()).path("code").asInt()).isEqualTo(10004);
    }

    @Test
    void isolationTenantSeesOnlyItsOwnData() throws Exception {
        String isolationStaff = sessions.login(staff(ISOLATION_TENANT, 2000000000000102L)).accessToken();
        var view = data(get("/api/v1/orders/" + ISOLATION_ORDER + "/shipment", isolationStaff));
        assertThat(view.path("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(view.path("trackingNo").asText()).isEqualTo("FIX1000003");
        assertThat(view.path("tracks").size()).isEqualTo(1);
        assertThat(view.path("tracks").get(0).path("description").asText()).contains("隔离");
    }

    /**
     * Exports the real Springdoc group {@code shipment-v1} so the repository snapshot in
     * {@code docs/api/shipment-v1.openapi.yaml} is generated output instead of the hand-written
     * review draft. The assertions pin the single operation, the shared envelope and the error
     * codes reviewers rely on; the YAML itself lands in {@code target/} and is copied to docs.
     */
    @Test
    void exportsActualSpringdocContractForShipmentV1() throws Exception {
        String token = sessions.login(staff(DEMO_TENANT, 2000000000000101L)).accessToken();
        assertThat(get("/api/v1/orders/" + IN_TRANSIT_ORDER + "/shipment", token).getStatusCode())
                .as("controller served before the contract snapshot is taken").isEqualTo(HttpStatus.OK);

        var response = http.getForEntity("/v3/api-docs/shipment-v1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode doc = json.readTree(response.getBody());

        List<String> operations = new ArrayList<>();
        doc.path("paths").fields().forEachRemaining(path -> path.getValue().fields()
                .forEachRemaining(operation -> operations.add(operation.getValue().path("operationId").asText())));
        assertThat(operations).containsExactly("getOrderShipment");
        assertThat(doc.path("info").path("title").asText()).isEqualTo("Merchant Shipment API");
        assertThat(doc.path("paths").path("/api/v1/orders/{orderId}/shipment").has("get")).isTrue();

        var responses = doc.path("paths").path("/api/v1/orders/{orderId}/shipment").path("get").path("responses");
        assertThat(responses.has("200")).isTrue();
        assertThat(responses.has("404")).isTrue();
        assertThat(doc.path("components").path("schemas").has("ApiResponseShipmentView")).isTrue();
        assertThat(doc.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(response.getBody()).contains("orderId", "trackingNo", "estimatedDeliveryAt", "10004", "no-store");

        String yaml = http.getForObject("/v3/api-docs.yaml/shipment-v1", String.class);
        assertThat(yaml).startsWith("openapi:");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/shipment-v1.openapi.yaml"), yaml);
    }
}
