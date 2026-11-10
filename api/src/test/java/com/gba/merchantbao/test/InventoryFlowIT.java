package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.SessionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP contract of the merchant inventory module: concurrent reserve can never oversell
 * (optimistic lock + per-retry revalidation), a repeated idempotent key applies exactly once,
 * and cross-tenant lookups are an indistinguishable 404/13001. Identity comes from the trusted
 * Actor only; the response envelope is the shared ApiResponse with 13xxx business codes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/inventory.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class InventoryFlowIT {
    static { DockerRequired.check(); }

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("inventory_flow").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();if (!REDIS.isRunning()) REDIS.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final long DEMO_TENANT = 1000000000000001L;
    private static final long ISOLATION_TENANT = 1000000000000002L;
    private static final long DEMO_SKU = 2000000000000902L;
    private static final long ISOLATION_SKU = 2000000000000908L;

    @Autowired SessionService sessions;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String token(long accountId, long merchantId, AccountRole role) {
        return sessions.login(new AuthPrincipal(accountId, merchantId, role)).accessToken();
    }

    private ResponseEntity<String> post(String path, String token, Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", "inventory-it-correlation");
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders();
        headers.set("X-Request-Id", "inventory-it-correlation");
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode envelope(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody());
    }

    /** Class-level fixtures are shared by all tests in this class; give each test a fresh row. */
    private void resetDemoStock() {
        jdbc.update("UPDATE inventory SET available_qty=10, reserved_qty=0, version=0 WHERE sku_id=?", DEMO_SKU);
    }

    private ResponseEntity<String> reserve(String token, String key, long skuId, int qty) {
        return post("/api/v1/merchant/inventory/reserve", token,
                Map.of("idempotentKey", key, "skuId", skuId, "qty", qty));
    }

    @Test
    void concurrentReserveCannotOversellAvailableStock() throws Exception {
        String staff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);

        // available=10; two competing reservations of 7 each — exactly one may win.
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<ResponseEntity<String>> a = () -> { start.await(); return reserve(staff, "IT-OS-A", DEMO_SKU, 7); };
            Callable<ResponseEntity<String>> b = () -> { start.await(); return reserve(staff, "IT-OS-B", DEMO_SKU, 7); };
            var fa = executor.submit(a); var fb = executor.submit(b); start.countDown();
            var responses = java.util.List.of(fa.get(60, TimeUnit.SECONDS), fb.get(60, TimeUnit.SECONDS));

            var statuses = responses.stream().mapToInt(r -> r.getStatusCode().value()).sorted().toArray();
            assertThat(statuses).as("bodies: %s | %s", responses.get(0).getBody(), responses.get(1).getBody())
                    .containsExactly(200, 409);

            // The loser is the 409 one; assert its business code is the available-shortage code.
            for (var response : responses) {
                if (response.getStatusCode().value() == 409) {
                    assertThat(envelope(response).get("code").asInt()).isEqualTo(13002);
                }
            }

            // No oversell: exactly one reservation landed (10 - 7).
            var stock = envelope(get("/api/v1/merchant/inventory/" + DEMO_SKU, staff));
            assertThat(stock.get("data").get("availableQty").asInt()).isEqualTo(3);
        }
    }

    @Test
    void sameIdempotentKeyAppliesTheStockChangeExactlyOnce() throws Exception {
        String staff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
        resetDemoStock();

        var first = reserve(staff, "IT-IDEM-1", DEMO_SKU, 4);
        var repeat = reserve(staff, "IT-IDEM-1", DEMO_SKU, 4);
        assertThat(first.getStatusCode()).as("first: %s", first.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(repeat.getStatusCode()).as("repeat: %s", repeat.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(envelope(first).get("code").asInt()).isZero();
        assertThat(envelope(repeat).get("code").asInt()).isZero();

        // The stock moved once (10 - 4), not twice; one SUCCESS idempotency row exists.
        var stock = envelope(get("/api/v1/merchant/inventory/" + DEMO_SKU, staff));
        assertThat(stock.get("data").get("availableQty").asInt()).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_idempotent WHERE idempotent_key='IT-IDEM-1' AND status='SUCCESS'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT change_available FROM inventory_transaction WHERE idempotent_key='IT-IDEM-1'",
                Integer.class)).isEqualTo(-4);

        // A different key is a new operation and moves the stock again.
        assertThat(reserve(staff, "IT-IDEM-2", DEMO_SKU, 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        var after = envelope(get("/api/v1/merchant/inventory/" + DEMO_SKU, staff));
        assertThat(after.get("data").get("availableQty").asInt()).isEqualTo(4);
    }

    @Test
    void crossTenantAndMissingStockAreIndistinguishable404() throws Exception {
        String demoStaff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
        resetDemoStock();
        String isolationStaff = token(2000000000000102L, ISOLATION_TENANT, AccountRole.MERCHANT_STAFF);

        // The isolation tenant cannot see or touch the demo tenant's SKU.
        var foreign = get("/api/v1/merchant/inventory/" + DEMO_SKU, isolationStaff);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelope(foreign).get("code").asInt()).isEqualTo(13001);

        var missing = get("/api/v1/merchant/inventory/999999999", demoStaff);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelope(missing).get("code").asInt()).isEqualTo(13001);

        // Isolation staff can always work with its own tenant stock.
        var own = get("/api/v1/merchant/inventory/" + ISOLATION_SKU, isolationStaff);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envelope(own).get("data").get("availableQty").asInt()).isEqualTo(5);

        // Reserve is likewise tenant-scoped: the demo tenant cannot reserve the isolation SKU.
        assertThat(reserve(demoStaff, "IT-CROSS-1", ISOLATION_SKU, 1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anonymousIs401AndInvalidBodiesAre400With13006() throws Exception {
        var anonymous = post("/api/v1/merchant/inventory/reserve", null,
                Map.of("idempotentKey", "k", "skuId", DEMO_SKU, "qty", 1));
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String staff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
        var invalid = post("/api/v1/merchant/inventory/reserve", staff,
                Map.of("idempotentKey", "IT-BAD-1", "skuId", DEMO_SKU, "qty", 0));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(envelope(invalid).get("code").asInt()).isEqualTo(13006);
    }
}
