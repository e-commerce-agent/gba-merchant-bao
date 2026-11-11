package com.gba.merchantbao.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.inventory.mapper.InventoryMapper;
import com.gba.merchantbao.inventory.service.InventoryAvailabilityQueryService;
import com.gba.merchantbao.product.cache.ProductCacheStore;
import com.gba.merchantbao.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Public availability uses real MySQL, Redis and HTTP, including warm-cache stock changes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "merchant.product.cache-enabled=true")
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/product_catalog.sql", "/db/fixture/inventory.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ProductAvailabilityIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("product_availability").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();
        if (!REDIS.isRunning()) REDIS.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final long DEMO = 1000000000000001L, ISO = 1000000000000002L;
    private static final long PRODUCT = 2600000000000201L, HIDDEN_PRODUCT = 2600000000000202L;
    private static final long SKU = 2600000000000301L, HIDDEN_SKU = 2600000000000302L, ISO_SKU = 2600000000000303L;
    private static final String ROOT = "/api/v1/stores/M_3C_DEMO";
    private static final List<String> PUBLIC_PATHS = List.of(ROOT + "/products", ROOT + "/products/" + PRODUCT, ROOT + "/skus/" + SKU);

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SessionService sessions;
    @Autowired InventoryAvailabilityQueryService availability;
    @Autowired ProductCacheStore cache;
    @MockitoSpyBean InventoryMapper inventory;
    @MockitoSpyBean ProductMapper products;
    @MockitoSpyBean StringRedisTemplate redis;

    @BeforeEach void resetFixtures() {
        jdbc.update("UPDATE merchant SET status='ACTIVE' WHERE id IN (?,?)", DEMO, ISO);
        redis.delete(List.of(ProductCacheStore.mappingKey("M_3C_DEMO"), ProductCacheStore.mappingKey("M_ISOLATION_TEST")));
        jdbc.update("DELETE FROM inventory_transaction");
        jdbc.update("DELETE FROM inventory_idempotent");
        jdbc.update("DELETE FROM inventory");
        jdbc.update("INSERT INTO inventory(merchant_id,sku_id,available_qty,reserved_qty,version) VALUES (?,?,10,0,0)", DEMO, SKU);
        jdbc.update("INSERT INTO inventory(merchant_id,sku_id,available_qty,reserved_qty,version) VALUES (?,?,5,0,0)", ISO, ISO_SKU);
        jdbc.update("UPDATE product SET status='PUBLISHED' WHERE id IN (?,2600000000000203)", PRODUCT);
        jdbc.update("UPDATE product SET status='UNPUBLISHED' WHERE id=?", HIDDEN_PRODUCT);
        jdbc.update("UPDATE product_sku SET status='ACTIVE'");
        cache.bump(DEMO);
        cache.bump(ISO);
        clearInvocations(inventory, products);
    }

    private ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders();
        headers.set("X-Request-Id", "product-availability-it");
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode data(String path, String token) throws Exception {
        var response = get(path, token);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        var body = json.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("requestId").asText()).isEqualTo("product-availability-it");
        assertThat(response.getBody()).doesNotContain("availableQty", "reservedQty", "available_qty", "reserved_qty",
                "inventoryId", "transactions", "idempotentKey", "operatorId", "version");
        return body.path("data");
    }

    private void assertPublicStock(boolean expected) throws Exception {
        for (String path : PUBLIC_PATHS) {
            JsonNode result = data(path, null);
            JsonNode sku = path.endsWith("/products") ? result.path("items").get(0).path("skus").get(0)
                    : path.contains("/products/") ? result.path("skus").get(0) : result;
            assertThat(sku.path("inStock").isBoolean()).isTrue();
            assertThat(sku.path("inStock").booleanValue()).as(path).isEqualTo(expected);
        }
    }

    private void changeStock(String operation, String key) {
        String token = sessions.login(new AuthPrincipal(2000000000000101L, DEMO, AccountRole.MERCHANT_STAFF)).accessToken();
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var response = http.exchange("/api/v1/inventory/" + operation, HttpMethod.POST,
                new HttpEntity<>(Map.of("skuId", SKU, "qty", 10, "idempotentKey", key), headers), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    }

    @Test void batchSqlTreatsMissingAndZeroAsFalseAndNeverReadsForeignOrUnrequestedStock() {
        assertThat(availability.inStock(DEMO, List.of(SKU, HIDDEN_SKU, ISO_SKU, 999L)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(SKU, true, HIDDEN_SKU, false, ISO_SKU, false, 999L, false));
        assertThat(availability.inStock(ISO, List.of(SKU, ISO_SKU)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(SKU, false, ISO_SKU, true));
        assertThat(inventory.selectInStockSkuIds(DEMO, List.of(HIDDEN_SKU))).isEmpty();
        jdbc.update("UPDATE inventory SET available_qty=0,reserved_qty=12 WHERE sku_id=?", SKU);
        assertThat(availability.inStock(DEMO, List.of(SKU))).containsEntry(SKU, false);
    }

    @Test void anonymousAndConsumerSeeOnlyBooleanStockForTheirChosenStore() throws Exception {
        assertPublicStock(true);
        String consumer = sessions.login(new AuthPrincipal(2000000000000201L, DEMO, AccountRole.CONSUMER)).accessToken();
        for (String path : PUBLIC_PATHS) data(path, consumer);
        jdbc.update("UPDATE inventory SET available_qty=0 WHERE sku_id=?", SKU);
        assertPublicStock(false);
        var iso = data("/api/v1/stores/M_ISOLATION_TEST/skus/" + ISO_SKU, null);
        assertThat(iso.path("inStock").booleanValue()).isTrue();
        jdbc.update("DELETE FROM inventory WHERE sku_id=?", SKU);
        assertPublicStock(false);
    }

    @Test void invisibleOrForeignCatalogIsFilteredBeforeAnyInventoryRead() throws Exception {
        for (String path : List.of(ROOT + "/products/" + HIDDEN_PRODUCT, ROOT + "/skus/" + HIDDEN_SKU,
                ROOT + "/skus/" + ISO_SKU, ROOT + "/products/2600000000000203", ROOT + "/skus/999")) {
            assertThat(get(path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        for (String status : List.of("DRAFT", "UNPUBLISHED", "DISCONTINUED")) {
            jdbc.update("UPDATE product SET status=? WHERE id=?", status, PRODUCT);
            cache.bump(DEMO);
            assertThat(get(ROOT + "/products/" + PRODUCT, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(get(ROOT + "/skus/" + SKU, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(data(ROOT + "/products", null).path("items")).isEmpty();
        }
        jdbc.update("UPDATE product SET status='PUBLISHED' WHERE id=?", PRODUCT);
        jdbc.update("UPDATE product_sku SET status='DISCONTINUED' WHERE id=?", SKU);
        cache.bump(DEMO);
        assertThat(get(ROOT + "/skus/" + SKU, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(data(ROOT + "/products/" + PRODUCT, null).path("skus")).isEmpty();
        verifyNoInteractions(inventory);
    }

    @Test void storeSlugUsesAuthNormalizationAndRejectsMissingOrDisabledStores() throws Exception {
        for (String path : PUBLIC_PATHS) {
            data(path.replace("M_3C_DEMO", "m_3c_demo"), null);
            assertThat(cache.readMerchantId("M_3C_DEMO")).isEqualTo(DEMO);
            assertThat(redis.hasKey(ProductCacheStore.mappingKey("m_3c_demo"))).isFalse();
            assertThat(get(path.replace("M_3C_DEMO", "NO_SUCH_STORE"), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(get(path.replace("M_3C_DEMO", "invalid!"), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        jdbc.update("UPDATE merchant SET status='DISABLED' WHERE id=?", ISO);
        for (String suffix : List.of("/products", "/products/2600000000000203", "/skus/" + ISO_SKU)) {
            assertThat(get("/api/v1/stores/M_ISOLATION_TEST" + suffix, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test void publicStoreSelectionIgnoresInvalidAndForeignTokensAndNeverUsesTheSession() throws Exception {
        String token = sessions.login(new AuthPrincipal(2000000000000201L, DEMO, AccountRole.CONSUMER)).accessToken();
        clearInvocations(sessions);
        for (String credential : List.of("invalid", token)) {
            var page = data("/api/v1/stores/M_ISOLATION_TEST/products", credential);
            assertThat(page.path("items").get(0).path("productId").asText()).isEqualTo("2600000000000203");
            assertThat(data("/api/v1/stores/M_ISOLATION_TEST/products/2600000000000203", credential)
                    .path("productId").asText()).isEqualTo("2600000000000203");
            assertThat(data("/api/v1/stores/M_ISOLATION_TEST/skus/" + ISO_SKU, credential)
                    .path("skuId").asText()).isEqualTo(Long.toString(ISO_SKU));
            assertThat(get("/api/v1/stores/M_ISOLATION_TEST/skus/" + SKU, credential).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        verifyNoInteractions(sessions);
    }

    @Test void oldRoutesAreRemovedAndPrivateAndWriteResourcesStillRequireAuthorization() throws Exception {
        String staff = sessions.login(new AuthPrincipal(2000000000000101L, DEMO, AccountRole.MERCHANT_STAFF)).accessToken();
        for (String path : PUBLIC_PATHS) {
            String old = path.replace("/api/v1/stores/", "/api/v1/public/stores/");
            assertThat(get(old, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(get(old, staff).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        for (String path : List.of(ROOT + "/orders", ROOT + "/products/" + PRODUCT + "/private",
                "/api/v1/products", "/api/v1/inventory/page")) {
            assertThat(get(path, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        var body = Map.of("productCode", "NO_WRITE", "name", "Not allowed");
        for (String path : List.of(ROOT + "/products", "/api/v1/products")) {
            assertThat(http.postForEntity(path, body, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        String consumer = sessions.login(new AuthPrincipal(2000000000000201L, DEMO, AccountRole.CONSUMER)).accessToken();
        var headers = new HttpHeaders();
        headers.setBearerAuth(consumer);
        assertThat(http.postForEntity("/api/v1/products", new HttpEntity<>(body, headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/products", staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/inventory/page", staff).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void pageBatchesOnlyVisibleSkusAndKeepsPageSizesAndCursorsSeparate() throws Exception {
        jdbc.update("UPDATE product SET status='PUBLISHED' WHERE id=?", HIDDEN_PRODUCT);
        cache.bump(DEMO);
        var first = data(ROOT + "/products?size=1", null);
        assertThat(first.path("items")).hasSize(1);
        assertThat(first.path("items").get(0).path("skus").get(0).path("inStock").booleanValue()).isFalse();
        clearInvocations(inventory);
        var all = data(ROOT + "/products?size=20", null);
        assertThat(all.path("items")).hasSize(2);
        verify(inventory).selectInStockSkuIds(DEMO, List.of(HIDDEN_SKU, SKU));
        verifyNoMoreInteractions(inventory);
        var second = data(ROOT + "/products?size=1&cursor=" + first.path("nextCursor").asText(), null);
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("items").get(0).path("skus").get(0).path("inStock").booleanValue()).isTrue();
    }

    @Test void warmedCatalogStillReflectsCommittedReserveAndReleaseWithoutInvalidation() throws Exception {
        assertPublicStock(true);
        long version = cache.version(DEMO);
        var cachedProduct = cache.readProduct(DEMO, PRODUCT);
        var cachedSku = cache.readSku(DEMO, SKU);
        var cachedPage = cache.readPage(DEMO, null, 20);
        assertThat(cachedProduct).isNotNull();
        assertThat(cachedSku).isNotNull();
        assertThat(cachedPage).isNotNull();
        assertThat(json.writeValueAsString(List.of(cachedProduct, cachedSku, cachedPage))).doesNotContain("inStock");
        changeStock("reserve", "public-stock-reserve");
        clearInvocations(products, inventory);
        assertPublicStock(false);
        verifyNoInteractions(products);
        verify(inventory, times(3)).selectInStockSkuIds(DEMO, List.of(SKU));
        assertThat(cache.version(DEMO)).isEqualTo(version);
        changeStock("release", "public-stock-release");
        assertPublicStock(true);
        assertThat(cache.readProduct(DEMO, PRODUCT)).isEqualTo(cachedProduct);
        assertThat(cache.readSku(DEMO, SKU)).isEqualTo(cachedSku);
        assertThat(cache.readPage(DEMO, null, 20)).isEqualTo(cachedPage);
    }

    @Test void oldCacheEntriesAreIgnoredAndRedisOutageFallsBackToCatalogStorage() throws Exception {
        String stale = "{\"skuId\":\"" + SKU + "\",\"skuCode\":\"STALE\",\"inStock\":false}";
        redis.opsForValue().set("gba:product:e:" + DEMO + ":v1:sku:" + SKU, stale);
        assertThat(data(ROOT + "/skus/" + SKU, null).path("skuCode").asText()).isEqualTo("FIX-SKU-1");
        doThrow(new DataAccessResourceFailureException("Redis unavailable")).when(redis).opsForValue();
        clearInvocations(products);
        assertPublicStock(true);
        verify(products, atLeastOnce()).find(DEMO, PRODUCT);
    }

    @Test void inventoryStorageFailureReturns503EvenWithAWarmCatalog() throws Exception {
        assertPublicStock(true);
        doThrow(new DataAccessResourceFailureException("MySQL unavailable"))
                .when(inventory).selectInStockSkuIds(eq(DEMO), anyList());
        for (String path : PUBLIC_PATHS) {
            var response = get(path, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            var body = json.readTree(response.getBody());
            assertThat(body.path("code").asInt()).isEqualTo(10500);
            assertThat(body.path("data").isNull()).isTrue();
        }
    }

    @Test void exportsActualProductOpenApiWithThePublicBooleanContract() throws Exception {
        assertPublicStock(true);
        var response = get("/v3/api-docs/product-v1", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var doc = json.readTree(response.getBody());
        var schemas = doc.path("components").path("schemas");
        var sku = schemas.path("PublicSkuView");
        assertThat(sku.path("properties").path("inStock").path("type").asText()).isEqualTo("boolean");
        assertThat(sku.path("properties").path("inStock").path("description").asText()).contains("缺少库存行", "每次请求");
        assertThat(sku.path("required").toString()).contains("inStock");
        assertThat(sku.path("properties").toString()).doesNotContain("availableQty", "reservedQty", "inventoryId");
        assertThat(schemas.path("PublicProductView").path("properties").path("skus").path("items").path("$ref").asText())
                .endsWith("/PublicSkuView");
        for (String path : List.of("/api/v1/stores/{storeSlug}/products",
                "/api/v1/stores/{storeSlug}/products/{productId}",
                "/api/v1/stores/{storeSlug}/skus/{skuId}")) {
            assertThat(doc.path("paths").path(path).has("get")).isTrue();
            assertThat(doc.path("paths").path(path).path("get").path("parameters").findValuesAsText("name"))
                    .contains("storeSlug").doesNotContain("merchantCode");
        }
        assertThat(doc.path("paths").toString()).doesNotContain("/api/v1/public/");
        assertThat(schemas.path("StaffLogin").path("properties").has("merchantCode")).isTrue();
        String yaml = http.getForObject("/v3/api-docs.yaml/product-v1", String.class);
        assertThat(yaml).startsWith("openapi:");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/product-v1.openapi.yaml"), yaml);
    }
}
