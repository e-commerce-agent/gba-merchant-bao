package com.gba.merchantbao.test.inventory;

import com.gba.merchantbao.test.support.DockerRequired;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.common.id.IdGenerator;
import com.gba.merchantbao.inventory.common.exception.InventoryBizException;
import com.gba.merchantbao.inventory.mapper.InventoryMapper;
import com.gba.merchantbao.inventory.mapper.InventoryTransactionMapper;
import com.gba.merchantbao.inventory.service.InventoryService;
import com.gba.merchantbao.product.service.ProductReferenceService;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP contract of the merchant inventory module: concurrent reserve can never oversell
 * (optimistic lock + per-retry revalidation), a repeated idempotent key applies exactly once,
 * and cross-tenant lookups are an indistinguishable 404/13001. Identity comes from the trusted
 * Actor only; the response envelope is the shared ApiResponse with 13xxx business codes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/product_catalog.sql", "/db/fixture/inventory.sql"},
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
    private static final long DEMO_SKU = 2600000000000301L;
    private static final long ISOLATION_SKU = 2600000000000303L;
    private static final long UNPUBLISHED_SKU = 2600000000000302L;

    @Autowired SessionService sessions;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductReferenceService references;
    @Autowired InventoryService inventoryService;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockitoSpyBean InventoryMapper inventoryMapper;
    @MockitoSpyBean InventoryTransactionMapper transactionMapper;
    @MockitoSpyBean IdGenerator idGenerator;

    @BeforeEach void resetFixtures() {
        jdbc.update("DELETE FROM inventory_transaction WHERE merchant_id IN (?,?)", DEMO_TENANT, ISOLATION_TENANT);
        jdbc.update("DELETE FROM inventory_idempotent WHERE merchant_id IN (?,?)", DEMO_TENANT, ISOLATION_TENANT);
        jdbc.update("DELETE FROM inventory WHERE merchant_id=? AND sku_id=?", DEMO_TENANT, UNPUBLISHED_SKU);
        jdbc.update("UPDATE product_sku SET status='ACTIVE' WHERE id IN (?,?,?)", DEMO_SKU, UNPUBLISHED_SKU, ISOLATION_SKU);
        jdbc.update("UPDATE product SET status='UNPUBLISHED' WHERE id=2600000000000202");
        resetDemoStock();
    }

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
        return post("/api/v1/inventory/reserve", token,
                Map.of("idempotentKey", key, "skuId", skuId, "qty", qty));
    }

    private ResponseEntity<String> adjust(String token, String key, long skuId, int delta) {
        return post("/api/v1/inventory/adjust", token,
                Map.of("idempotentKey", key, "skuId", skuId, "adjustQty", delta));
    }

    private String staffToken() {
        return token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
    }

    private void assertError(ResponseEntity<String> response, int status, int code) throws Exception {
        assertThat(response.getStatusCode().value()).as("%s", response.getBody()).isEqualTo(status);
        assertThat(envelope(response).get("code").asInt()).isEqualTo(code);
    }

    private Map<String, Object> stock(long skuId) {
        return jdbc.queryForMap("SELECT available_qty,reserved_qty,version FROM inventory WHERE merchant_id=? AND sku_id=?",
                DEMO_TENANT, skuId);
    }

    private int ledgerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction", Integer.class);
    }

    @Test void exactStockQueriesRejectConsumersAndAnonymousBeforeReadingInventory() throws Exception {
        String consumer = token(2000000000000201L, DEMO_TENANT, AccountRole.CONSUMER);
        clearInvocations(inventoryMapper);
        for (String path : java.util.List.of("/api/v1/inventory/" + DEMO_SKU,
                "/api/v1/inventory/page", "/api/v1/inventory/999999999")) {
            var forbidden = get(path, consumer);
            assertError(forbidden, 400, 13006);
            assertThat(envelope(forbidden).get("data").isNull()).isTrue();
            var anonymous = get(path, null);
            assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(envelope(anonymous).get("data").isNull()).isTrue();
        }
        // The same boundary also applies to direct Java callers of the actor-based queries.
        var actor = new Actor(new AuthPrincipal(2000000000000201L, DEMO_TENANT, AccountRole.CONSUMER), "test-session");
        assertThatThrownBy(() -> inventoryService.query(actor, DEMO_SKU))
                .isInstanceOfSatisfying(InventoryBizException.class, e -> assertThat(e.getCode()).isEqualTo(13006));
        assertThatThrownBy(() -> inventoryService.pageList(actor, 1, 20))
                .isInstanceOfSatisfying(InventoryBizException.class, e -> assertThat(e.getCode()).isEqualTo(13006));
        verifyNoInteractions(inventoryMapper);
    }

    @Test void staffReadsAndPagesOnlyItsAuthenticatedMerchantOnTheUnifiedRoute() throws Exception {
        String demoStaff = staffToken();
        String isolationStaff = token(2000000000000102L, ISOLATION_TENANT, AccountRole.MERCHANT_STAFF);
        var own = get("/api/v1/inventory/" + DEMO_SKU + "?merchantId=" + ISOLATION_TENANT, demoStaff);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envelope(own).get("data").get("availableQty").asInt()).isEqualTo(10);

        var demoPage = get("/api/v1/inventory/page?pageNo=1&pageSize=100&merchantId=" + ISOLATION_TENANT, demoStaff);
        var isolationPage = get("/api/v1/inventory/page?pageNo=1&pageSize=100&merchantId=" + DEMO_TENANT, isolationStaff);
        var responses = java.util.List.of(demoPage, isolationPage);
        long[] expectedSkus = {DEMO_SKU, ISOLATION_SKU};
        for (int i = 0; i < responses.size(); i++) {
            var response = responses.get(i);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var page = envelope(response).get("data");
            assertThat(page.get("total").asLong()).isOne();
            assertThat(page.get("records").size()).isOne();
            assertThat(page.get("records").get(0).get("skuId").asLong()).isEqualTo(expectedSkus[i]);
            assertThat(page.get("current").asInt()).isOne();
            assertThat(page.get("size").asInt()).isEqualTo(100);
        }
        assertError(get("/api/v1/inventory/" + ISOLATION_SKU, demoStaff), 404, 13001);
    }

    @Test void oldMerchantPrefixedRoutesAreRetiredWithoutChangingStock() {
        String staff = staffToken();
        var before = stock(DEMO_SKU);
        assertThat(get("/api/v1/merchant/inventory/" + DEMO_SKU, staff).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/merchant/inventory/page", staff).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/v1/merchant/inventory/adjust", staff,
                Map.of("idempotentKey", "IT-OLD-ROUTE", "skuId", DEMO_SKU, "adjustQty", 3)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stock(DEMO_SKU)).isEqualTo(before);
        assertThat(ledgerCount()).isZero();
    }

    @Test void catalogReferencesIncludeUnpublishedAndDraftSkusButHideOtherMerchants() {
        for (String productStatus : java.util.List.of("DRAFT", "UNPUBLISHED")) {
            jdbc.update("UPDATE product SET status=? WHERE id=2600000000000202", productStatus);
            var sku = references.findSku(DEMO_TENANT, UNPUBLISHED_SKU).orElseThrow();
            assertThat(sku.productId()).isEqualTo(2600000000000202L);
            assertThat(sku.active()).isTrue();
        }
        jdbc.update("UPDATE product_sku SET status='DISCONTINUED' WHERE id=?", UNPUBLISHED_SKU);
        assertThat(references.findSku(DEMO_TENANT, UNPUBLISHED_SKU).orElseThrow().active()).isFalse();
        assertThat(references.findSku(ISOLATION_TENANT, UNPUBLISHED_SKU)).isEmpty();
        assertThat(references.findSku(DEMO_TENANT, 999999999L)).isEmpty();
    }

    @Test void unknownAndForeignSkusCannotCreateStockOrLedgerEvenWithASuccessfulKey() throws Exception {
        String staff = staffToken();
        assertThat(adjust(staff, "IT-CATALOG-OK", DEMO_SKU, 1).getStatusCode()).isEqualTo(HttpStatus.OK);
        var before = stock(DEMO_SKU);
        int logs = ledgerCount();
        for (long skuId : new long[]{ISOLATION_SKU, 999999999L}) {
            assertError(adjust(staff, "IT-CATALOG-OK", skuId, 5), 404, 13001);
            for (String operation : java.util.List.of("reserve", "release", "deduct")) {
                assertError(post("/api/v1/inventory/" + operation, staff,
                        Map.of("idempotentKey", "IT-CATALOG-OK", "skuId", skuId, "qty", 1)), 404, 13001);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE merchant_id=? AND sku_id=?",
                    Integer.class, DEMO_TENANT, skuId)).isZero();
        }
        assertThat(stock(DEMO_SKU)).isEqualTo(before);
        assertThat(ledgerCount()).isEqualTo(logs);
    }

    @Test void discontinuedSkuRejectsNewSupplyAndReserveButAllowsCleanup() throws Exception {
        String staff = staffToken();
        jdbc.update("UPDATE inventory SET reserved_qty=4 WHERE merchant_id=? AND sku_id=?", DEMO_TENANT, DEMO_SKU);
        jdbc.update("UPDATE product_sku SET status='DISCONTINUED' WHERE id IN (?,?)", DEMO_SKU, UNPUBLISHED_SKU);
        var before = stock(DEMO_SKU);
        assertError(adjust(staff, "IT-DISC-ADD", DEMO_SKU, 2), 409, 13008);
        assertError(adjust(staff, "IT-DISC-INIT", UNPUBLISHED_SKU, 2), 409, 13008);
        assertError(reserve(staff, "IT-DISC-RESERVE", DEMO_SKU, 1), 409, 13008);
        assertThat(stock(DEMO_SKU)).isEqualTo(before);
        assertThat(ledgerCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE sku_id=?", Integer.class, UNPUBLISHED_SKU)).isZero();
        assertThat(adjust(staff, "IT-DISC-LOSS", DEMO_SKU, -2).getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String operation : java.util.List.of("release", "deduct")) {
            assertThat(post("/api/v1/inventory/" + operation, staff,
                    Map.of("idempotentKey", "IT-DISC-" + operation, "skuId", DEMO_SKU, "qty", 2))
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(stock(DEMO_SKU)).containsEntry("available_qty", 10).containsEntry("reserved_qty", 0).containsEntry("version", 3);
        assertThat(ledgerCount()).isEqualTo(3);
    }

    @Test void firstPositiveAdjustmentStocksUnpublishedSkuOnceWithZeroBasedLedger() throws Exception {
        String staff = staffToken();
        assertThat(adjust(staff, "IT-INIT", UNPUBLISHED_SKU, 6).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adjust(staff, "IT-INIT", UNPUBLISHED_SKU, 6).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(UNPUBLISHED_SKU)).containsEntry("available_qty", 6).containsEntry("reserved_qty", 0).containsEntry("version", 1);
        assertThat(ledgerCount()).isOne();
        assertThat(jdbc.queryForMap("SELECT before_available,after_available,before_reserved,after_reserved FROM inventory_transaction WHERE idempotent_key='IT-INIT'"))
                .containsEntry("before_available", 0).containsEntry("after_available", 6)
                .containsEntry("before_reserved", 0).containsEntry("after_reserved", 0);
        assertThat(jdbc.queryForObject("SELECT status FROM product WHERE id=2600000000000202", String.class)).isEqualTo("UNPUBLISHED");
    }

    @Test void missingStockIsNotInitializedByNegativeAdjustmentOrOtherOperations() throws Exception {
        String staff = staffToken();
        assertError(adjust(staff, "IT-MISSING-LOSS", UNPUBLISHED_SKU, -1), 404, 13001);
        for (String operation : java.util.List.of("reserve", "release", "deduct")) {
            assertError(post("/api/v1/inventory/" + operation, staff,
                    Map.of("idempotentKey", "IT-MISSING-" + operation, "skuId", UNPUBLISHED_SKU, "qty", 1)), 404, 13001);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE sku_id=?", Integer.class, UNPUBLISHED_SKU)).isZero();
        assertThat(ledgerCount()).isZero();
    }

    @Test void simultaneousFirstAdjustmentsSerializeThroughTheMerchantSkuUniqueKey() throws Exception {
        String staff = staffToken();
        // Force both transactions to observe the missing row before either INSERT.
        var initialReads = new java.util.concurrent.atomic.AtomicInteger();
        var bothRead = new CountDownLatch(2);
        doAnswer(invocation -> {
            var result = sqlSession.getMapper(InventoryMapper.class).selectByMerchantAndSku(DEMO_TENANT, UNPUBLISHED_SKU);
            if (initialReads.incrementAndGet() <= 2) {
                assertThat(result).isNull();
                bothRead.countDown();
                assertThat(bothRead.await(30, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(inventoryMapper).selectByMerchantAndSku(DEMO_TENANT, UNPUBLISHED_SKU);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> adjust(staff, "IT-INIT-A", UNPUBLISHED_SKU, 3));
            var b = executor.submit(() -> adjust(staff, "IT-INIT-B", UNPUBLISHED_SKU, 5));
            for (var future : java.util.List.of(a, b)) {
                var response = future.get(60, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.OK);
            }
        }
        assertThat(stock(UNPUBLISHED_SKU)).containsEntry("available_qty", 8).containsEntry("reserved_qty", 0).containsEntry("version", 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE merchant_id=? AND sku_id=?",
                Integer.class, DEMO_TENANT, UNPUBLISHED_SKU)).isOne();
        var logs = jdbc.queryForList("SELECT before_available,after_available FROM inventory_transaction WHERE sku_id=? ORDER BY id", UNPUBLISHED_SKU);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0)).containsEntry("before_available", 0);
        assertThat(logs.get(1)).containsEntry("before_available", logs.get(0).get("after_available")).containsEntry("after_available", 8);
    }

    @Test void sameKeyDuringFirstAdjustmentNeverCreatesDuplicateStockOrLedger() throws Exception {
        String staff = staffToken();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<ResponseEntity<String>> request = () -> {
                start.await();
                return adjust(staff, "IT-INIT-SAME", UNPUBLISHED_SKU, 7);
            };
            var a = executor.submit(request);
            var b = executor.submit(request);
            start.countDown();
            var responses = java.util.List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
            assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 200).count()).isGreaterThanOrEqualTo(1);
            for (var response : responses) {
                if (response.getStatusCode().value() != 200) assertError(response, 409, 13004);
            }
        }
        assertThat(adjust(staff, "IT-INIT-SAME", UNPUBLISHED_SKU, 7).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(UNPUBLISHED_SKU)).containsEntry("available_qty", 7).containsEntry("version", 1);
        assertThat(ledgerCount()).isOne();
    }

    @Test void versionConflictRetriesNewTransactionAndExhaustionRollsBackInitialization() throws Exception {
        String staff = staffToken();
        doReturn(0).doAnswer(i -> sqlSession.getMapper(InventoryMapper.class).updateStockWithVersion(
                i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4)))
                .when(inventoryMapper).updateStockWithVersion(
                eq(DEMO_TENANT), eq(UNPUBLISHED_SKU), anyInt(), anyInt(), anyInt());
        assertThat(adjust(staff, "IT-RETRY-OK", UNPUBLISHED_SKU, 4).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(UNPUBLISHED_SKU)).containsEntry("available_qty", 4).containsEntry("version", 1);
        assertThat(ledgerCount()).isOne();
        verify(inventoryMapper, times(2)).insertZeroStock(anyLong(), eq(DEMO_TENANT), eq(UNPUBLISHED_SKU));

        doReturn(0).when(inventoryMapper).updateStockWithVersion(eq(DEMO_TENANT), eq(DEMO_SKU), anyInt(), anyInt(), anyInt());
        var before = stock(DEMO_SKU);
        assertError(adjust(staff, "IT-RETRY-FAIL", DEMO_SKU, 3), 409, 13003);
        assertThat(stock(DEMO_SKU)).isEqualTo(before);
        assertThat(ledgerCount()).isOne();
        verify(inventoryMapper, times(3)).updateStockWithVersion(DEMO_TENANT, DEMO_SKU, 13, 0, 0);
    }

    @Test void ledgerFailureRollsBackStockCreationAndFailedKeyCanRetry() throws Exception {
        String staff = staffToken();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("forced ledger failure"))
                .when(transactionMapper).insert(any());
        assertError(adjust(staff, "IT-ATOMIC", UNPUBLISHED_SKU, 4), 500, 13999);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE sku_id=?", Integer.class, UNPUBLISHED_SKU)).isZero();
        assertThat(ledgerCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_idempotent WHERE idempotent_key='IT-ATOMIC'", String.class)).isEqualTo("FAILED");
        long acquiredId = jdbc.queryForObject("SELECT id FROM inventory_idempotent WHERE idempotent_key='IT-ATOMIC'", Long.class);
        reset(transactionMapper);
        assertThat(adjust(staff, "IT-ATOMIC", UNPUBLISHED_SKU, 4).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(UNPUBLISHED_SKU)).containsEntry("available_qty", 4).containsEntry("version", 1);
        assertThat(ledgerCount()).isOne();
        assertThat(jdbc.queryForList("SELECT id FROM inventory_idempotent WHERE idempotent_key='IT-ATOMIC'", Long.class))
                .containsExactly(acquiredId);
    }

    @Test void allNewInventoryRowsPersistIdsFromTheInjectedGeneratorAndReplayPreservesThem() throws Exception {
        String staff = staffToken();
        long acquiredId = 2700000000000701L, stockId = 2700000000000521L, ledgerId = 2700000000000693L;
        // Deliberately nonconsecutive: allocated-but-unused IDs need never be recycled.
        doReturn(acquiredId, stockId, ledgerId, 2700000000000799L).when(idGenerator).nextId();
        assertThat(adjust(staff, "IT-IDS", UNPUBLISHED_SKU, 6).getStatusCode()).isEqualTo(HttpStatus.OK);
        var stockBefore = jdbc.queryForMap("SELECT * FROM inventory WHERE sku_id=?", UNPUBLISHED_SKU);
        var ledgerBefore = jdbc.queryForList("SELECT * FROM inventory_transaction WHERE idempotent_key='IT-IDS'");
        assertThat(stockBefore).containsEntry("id", stockId);
        assertThat(ledgerBefore).singleElement().satisfies(row -> assertThat(row).containsEntry("id", ledgerId));
        assertThat(jdbc.queryForList("SELECT id FROM inventory_idempotent WHERE idempotent_key='IT-IDS'", Long.class))
                .containsExactly(acquiredId);
        assertThat(adjust(staff, "IT-IDS", UNPUBLISHED_SKU, 6).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForMap("SELECT * FROM inventory WHERE sku_id=?", UNPUBLISHED_SKU)).isEqualTo(stockBefore);
        assertThat(jdbc.queryForList("SELECT * FROM inventory_transaction WHERE idempotent_key='IT-IDS'")).isEqualTo(ledgerBefore);
        assertThat(jdbc.queryForList("SELECT id FROM inventory_idempotent WHERE idempotent_key='IT-IDS'", Long.class))
                .containsExactly(acquiredId);
        verify(idGenerator, times(4)).nextId();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2})
    void generatorFailureAtEachInsertCannotLeavePartialStockLedgerOrSuccess(int successfulAllocations) throws Exception {
        String staff = staffToken();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            int allocation = calls.getAndIncrement();
            if (allocation >= successfulAllocations) throw new IllegalStateException("forced ID allocation failure");
            return 2700000000000800L + allocation;
        }).when(idGenerator).nextId();
        var response = adjust(staff, "IT-ID-FAIL", UNPUBLISHED_SKU, 4);
        assertError(response, 500, 13999);
        assertThat(response.getBody()).doesNotContain("forced ID allocation failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE sku_id=?", Integer.class, UNPUBLISHED_SKU)).isZero();
        assertThat(ledgerCount()).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM inventory_idempotent WHERE idempotent_key='IT-ID-FAIL'", String.class))
                .containsExactly(successfulAllocations == 0 ? new String[0] : new String[]{"FAILED"});
        reset(idGenerator);
        assertThat(adjust(staff, "IT-ID-FAIL", UNPUBLISHED_SKU, 4).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(UNPUBLISHED_SKU)).containsEntry("available_qty", 4).containsEntry("version", 1);
        assertThat(ledgerCount()).isOne();
        if (successfulAllocations > 0) {
            assertThat(jdbc.queryForList("SELECT id FROM inventory_idempotent WHERE idempotent_key='IT-ID-FAIL'", Long.class))
                    .containsExactly(2700000000000800L);
        }
    }

    @Test void generatorFailureDuringLedgerAllocationRollsBackAnExistingStockUpdate() throws Exception {
        String staff = staffToken();
        var before = stock(DEMO_SKU);
        doReturn(2700000000000801L).doThrow(new IllegalStateException("forced ID allocation failure"))
                .when(idGenerator).nextId();
        assertError(reserve(staff, "IT-ID-EXISTING", DEMO_SKU, 2), 500, 13999);
        assertThat(stock(DEMO_SKU)).isEqualTo(before);
        assertThat(ledgerCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_idempotent WHERE idempotent_key='IT-ID-EXISTING'", String.class))
                .isEqualTo("FAILED");
    }

    @Test void successfulReplayStillRequiresCurrentIdentityAndCatalogState() throws Exception {
        String staff = staffToken();
        assertThat(reserve(staff, "IT-REPLAY", DEMO_SKU, 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        var before = stock(DEMO_SKU);
        String consumer = token(2000000000000201L, DEMO_TENANT, AccountRole.CONSUMER);
        assertError(reserve(consumer, "IT-REPLAY", DEMO_SKU, 2), 400, 13006);
        assertThat(post("/api/v1/inventory/reserve", null,
                Map.of("idempotentKey", "IT-REPLAY", "skuId", DEMO_SKU, "qty", 2)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String foreignStaff = token(2000000000000102L, ISOLATION_TENANT, AccountRole.MERCHANT_STAFF);
        assertError(reserve(foreignStaff, "IT-REPLAY", DEMO_SKU, 2), 404, 13001);
        jdbc.update("UPDATE product_sku SET status='DISCONTINUED' WHERE id=?", DEMO_SKU);
        assertError(reserve(staff, "IT-REPLAY", DEMO_SKU, 2), 409, 13008);
        assertThat(stock(DEMO_SKU)).isEqualTo(before);
        assertThat(ledgerCount()).isOne();
    }

    @Test
    void concurrentReserveCannotOversellAvailableStock() throws Exception {
        String staff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
        resetDemoStock();

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
            var stock = envelope(get("/api/v1/inventory/" + DEMO_SKU, staff));
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
        var stock = envelope(get("/api/v1/inventory/" + DEMO_SKU, staff));
        assertThat(stock.get("data").get("availableQty").asInt()).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_idempotent WHERE idempotent_key='IT-IDEM-1' AND status='SUCCESS'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT change_available FROM inventory_transaction WHERE idempotent_key='IT-IDEM-1'",
                Integer.class)).isEqualTo(-4);

        // A different key is a new operation and moves the stock again.
        assertThat(reserve(staff, "IT-IDEM-2", DEMO_SKU, 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        var after = envelope(get("/api/v1/inventory/" + DEMO_SKU, staff));
        assertThat(after.get("data").get("availableQty").asInt()).isEqualTo(4);
    }

    @Test
    void crossTenantAndMissingStockAreIndistinguishable404() throws Exception {
        String demoStaff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
        resetDemoStock();
        String isolationStaff = token(2000000000000102L, ISOLATION_TENANT, AccountRole.MERCHANT_STAFF);

        // The isolation tenant cannot see or touch the demo tenant's SKU.
        var foreign = get("/api/v1/inventory/" + DEMO_SKU, isolationStaff);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelope(foreign).get("code").asInt()).isEqualTo(13001);

        var missing = get("/api/v1/inventory/999999999", demoStaff);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelope(missing).get("code").asInt()).isEqualTo(13001);

        // Isolation staff can always work with its own tenant stock.
        var own = get("/api/v1/inventory/" + ISOLATION_SKU, isolationStaff);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envelope(own).get("data").get("availableQty").asInt()).isEqualTo(5);

        // Reserve is likewise tenant-scoped: the demo tenant cannot reserve the isolation SKU.
        assertThat(reserve(demoStaff, "IT-CROSS-1", ISOLATION_SKU, 1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anonymousIs401AndInvalidBodiesAre400With13006() throws Exception {
        var anonymous = post("/api/v1/inventory/reserve", null,
                Map.of("idempotentKey", "k", "skuId", DEMO_SKU, "qty", 1));
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String staff = token(2000000000000101L, DEMO_TENANT, AccountRole.MERCHANT_STAFF);
        var invalid = post("/api/v1/inventory/reserve", staff,
                Map.of("idempotentKey", "IT-BAD-1", "skuId", DEMO_SKU, "qty", 0));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(envelope(invalid).get("code").asInt()).isEqualTo(13006);
    }

    @Test
    void inventoryAndLedgerRequireSameMerchantCatalogSkuWithoutCascadeDelete() {
        long ledgerSku = 2600000000000302L; // No stock row: the ledger FK must protect this SKU itself.
        jdbc.update("INSERT INTO inventory_transaction (id, merchant_id, sku_id, biz_type, change_available, change_reserved, " +
                        "before_available, after_available, before_reserved, after_reserved, operator_id) " +
                        "VALUES (2600000000000601, ?, ?, 'ADJUST', 1, 0, 0, 1, 0, 0, 'schema-it')",
                DEMO_TENANT, ledgerSku);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO inventory (id,merchant_id,sku_id,available_qty,reserved_qty,version) VALUES (2600000000000599,?,?,1,0,0)",
                DEMO_TENANT, 999999999L))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO inventory (id,merchant_id,sku_id,available_qty,reserved_qty,version) VALUES (2600000000000599,?,?,1,0,0)",
                ISOLATION_TENANT, DEMO_SKU))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO inventory_transaction (id,merchant_id,sku_id,biz_type,change_available,change_reserved,before_available,after_available,before_reserved,after_reserved) " +
                        "VALUES (2600000000000699,?,?,'ADJUST',1,0,0,1,0,0)", DEMO_TENANT, 999999999L)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO inventory_transaction (id,merchant_id,sku_id,biz_type,change_available,change_reserved,before_available,after_available,before_reserved,after_reserved) " +
                        "VALUES (2600000000000699,?,?,'ADJUST',1,0,0,1,0,0)", ISOLATION_TENANT, DEMO_SKU)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "DELETE FROM product_sku WHERE id=?", ledgerSku)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE merchant_id=? AND sku_id=?",
                Integer.class, DEMO_TENANT, ledgerSku)).isOne();
    }
}
