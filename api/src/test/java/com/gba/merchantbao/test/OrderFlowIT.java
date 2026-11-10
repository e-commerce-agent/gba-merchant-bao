package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.SessionService;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP contract of the read-only order module: consumer own-scope, staff merchant-scope,
 * indistinguishable 404 for out-of-scope/missing, envelope and pagination rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/orders.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderFlowIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("order_flow").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        // BEFORE_TEST_CLASS @Sql needs the context before the extension's beforeAll callback.
        if (!MYSQL.isRunning()) MYSQL.start();if (!REDIS.isRunning()) REDIS.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);r.add("spring.datasource.username", MYSQL::getUsername);r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired SessionService sessions;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    long tenant(String code) { return jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code=?", Long.class, code); }
    String token(long accountId, String merchantCode, AccountRole role) {
        return sessions.login(new AuthPrincipal(accountId, tenant(merchantCode), role)).accessToken();
    }
    ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders();
        headers.set("X-Request-Id", "order-it-correlation");
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    JsonNode envelope(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()); }

    @Test
    void consumerListsOnlyOwnOrdersWithEnvelopeAndPagination() throws Exception {
        var token = token(2000000000000201L, "M_3C_DEMO", AccountRole.CONSUMER);
        var response = get("/api/v1/orders", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = envelope(response);
        assertThat(body.get("code").asInt()).isZero();
        assertThat(body.get("requestId").asText()).isEqualTo("order-it-correlation");
        var data = body.get("data");
        assertThat(data.get("total").asLong()).isEqualTo(2);
        assertThat(data.get("current").asInt()).isEqualTo(1);
        assertThat(data.get("size").asInt()).isEqualTo(10);
        assertThat(data.get("records")).hasSize(2);
        // IDs are strings to survive JavaScript BIGINT precision.
        assertThat(data.get("records").get(0).get("orderId").isTextual()).isTrue();

        var second = envelope(get("/api/v1/orders?pageNum=2&pageSize=1", token));
        assertThat(second.get("data").get("records")).hasSize(1);
        var empty = envelope(get("/api/v1/orders?pageNum=3&pageSize=1", token));
        assertThat(empty.get("data").get("records")).isEmpty();
        assertThat(empty.get("data").get("total").asLong()).isEqualTo(2);
    }

    @Test
    void pageSizeAboveCapIsRejectedAndAnonymousIsUnauthorized() {
        var token = token(2000000000000201L, "M_3C_DEMO", AccountRole.CONSUMER);
        assertThat(get("/api/v1/orders?pageSize=101", token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/orders?pageNum=0", token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var anonymous = get("/api/v1/orders", null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/orders", "not-a-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void detailReturnsSnapshotAndOutOfScopeOrMissingIsIndistinguishable404() throws Exception {
        var consumer = token(2000000000000201L, "M_3C_DEMO", AccountRole.CONSUMER);
        var response = get("/api/v1/orders/2000000000000301", consumer);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = envelope(response).get("data");
        assertThat(data.get("orderNo").asText()).isEqualTo("DEV-2026-0901");
        assertThat(data.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.get("payStatus").asText()).isEqualTo("PAID");
        assertThat(data.get("totalAmount").asDouble()).isEqualTo(158.00);
        assertThat(data.get("items")).hasSize(2);
        assertThat(data.get("items").get(0).get("productName").asText()).isEqualTo("65W 双口充电器");
        assertThat(data.get("items").get(0).get("skuAttributes").asText()).contains("USB-C");
        assertThat(data.get("items").get(0).get("amount").asDouble()).isEqualTo(79.00);

        // Another consumer in the same tenant, the other tenant's same order id,
        // the other tenant's order number space and a nonexistent id are all bare 404/10004.
        var otherConsumer = token(2000000000000202L, "M_3C_DEMO", AccountRole.CONSUMER);
        var foreign = token(2000000000000203L, "M_ISOLATION_TEST", AccountRole.CONSUMER);
        for (var t : new String[]{otherConsumer, foreign}) {
            var notFound = get("/api/v1/orders/2000000000000301", t);
            assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(envelope(notFound).get("code").asInt()).isEqualTo(10004);
        }
        var missing = get("/api/v1/orders/999999999", consumer);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelope(missing).get("code").asInt()).isEqualTo(10004);
        var malformed = get("/api/v1/orders/abc", consumer);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void staffSeesWholeMerchantButNeverAnotherTenant() throws Exception {
        var staff = token(2000000000000101L, "M_3C_DEMO", AccountRole.MERCHANT_STAFF);
        var foreignStaff = token(2000000000000102L, "M_ISOLATION_TEST", AccountRole.MERCHANT_STAFF);

        var list = envelope(get("/api/v1/orders", staff));
        assertThat(list.get("data").get("total").asLong()).isEqualTo(3);
        var foreignList = envelope(get("/api/v1/orders", foreignStaff));
        assertThat(foreignList.get("data").get("total").asLong()).isEqualTo(1);
        assertThat(foreignList.get("data").get("records").get(0).get("orderNo").asText()).isEqualTo("DEV-2026-0901");

        assertThat(get("/api/v1/orders/2000000000000303", staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Isolation tenant owns its own DEV-2026-0901, never the demo tenant rows.
        var own = get("/api/v1/orders/2000000000000304", foreignStaff);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envelope(own).get("data").get("items").get(0).get("productName").asText()).isEqualTo("隔离测试充电线");
        var cross = get("/api/v1/orders/2000000000000304", staff);
        assertThat(cross.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelope(cross).get("code").asInt()).isEqualTo(10004);
    }
}
