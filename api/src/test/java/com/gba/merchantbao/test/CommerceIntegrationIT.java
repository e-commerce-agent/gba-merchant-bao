package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.inventory.dto.InventoryAdjustDTO;
import com.gba.merchantbao.inventory.common.exception.InventoryBizException;
import com.gba.merchantbao.inventory.service.InventoryService;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.order.service.OrderQueryService;
import com.gba.merchantbao.product.cache.ProductCacheStore;
import com.gba.merchantbao.product.service.ProductQueryService;
import com.gba.merchantbao.shipment.service.ShipmentQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import static org.assertj.core.api.Assertions.*;

/** All three domain seams run together, with real persistence and a warm catalog cache. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-circular-references=false", "merchant.product.cache-enabled=true"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts = {"/db/fixture/auth_accounts.sql", "/db/fixture/product_catalog.sql",
        "/db/fixture/orders.sql", "/db/fixture/shipment.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CommerceIntegrationIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("commerce_integration").withUsername("test").withPassword("test");
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
    @Autowired ProductQueryService products;
    @Autowired InventoryService inventory;
    @Autowired OrderQueryService orders;
    @Autowired ShipmentQueryService shipments;
    @Autowired ProductCacheStore cache;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test void commerceExportsContainOnlyTheirOwnModuleOperations() throws Exception {
        var response = http.getForEntity("/v3/api-docs/inventory-v1", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var api = json.readTree(response.getBody());
        assertThat(api.path("paths").size()).isEqualTo(6);
        for (String suffix : new String[]{"adjust", "reserve", "release", "deduct", "page", "{skuId}"}) {
            var path = api.path("paths").path("/api/v1/inventory/" + suffix);
            String method = suffix.equals("page") || suffix.equals("{skuId}") ? "get" : "post";
            assertThat(path.path(method).path("security").get(0).has("bearerAuth")).isTrue();
            assertThat(path.path(method).path("responses").has("401")).isTrue();
        }
        assertThat(api.path("components").path("schemas").has("InventoryAdjustDTO")).isTrue();
        assertThat(api.path("components").path("schemas").has("InventoryVO")).isTrue();
        var product = json.readTree(http.getForObject("/v3/api-docs/product-v1", String.class));
        var productPaths = new java.util.HashSet<String>();
        product.path("paths").fieldNames().forEachRemaining(productPaths::add);
        assertThat(productPaths).containsExactlyInAnyOrder(
                "/api/v1/product-categories", "/api/v1/product-categories/{categoryId}",
                "/api/v1/products", "/api/v1/products/{productId}",
                "/api/v1/products/{productId}/publish", "/api/v1/products/{productId}/unpublish",
                "/api/v1/products/{productId}/discontinue", "/api/v1/products/{productId}/attributes",
                "/api/v1/products/{productId}/skus", "/api/v1/skus/{skuId}",
                "/api/v1/stores/{storeSlug}/products", "/api/v1/stores/{storeSlug}/products/{productId}",
                "/api/v1/stores/{storeSlug}/skus/{skuId}");
        var order = json.readTree(http.getForObject("/v3/api-docs/order-v1", String.class));
        assertThat(order.path("paths").size()).isEqualTo(2);
        assertThat(order.path("paths").has("/api/v1/orders")).isTrue();
        assertThat(order.path("paths").has("/api/v1/orders/{orderId}")).isTrue();
        var shipment = json.readTree(http.getForObject("/v3/api-docs/shipment-v1", String.class));
        assertThat(shipment.path("paths").size()).isEqualTo(1);
        assertThat(shipment.path("paths").has("/api/v1/orders/{orderId}/shipment")).isTrue();
    }

    @Test void stockChangesAndHistoricalSnapshotsCoexistWithoutTenantLeaks() {
        long demo = 1000000000000001L, iso = 1000000000000002L, sku = 2600000000000301L;
        var staff = actor(demo, 2000000000000101L, AccountRole.MERCHANT_STAFF);
        var foreignStaff = actor(iso, 2000000000000102L, AccountRole.MERCHANT_STAFF);
        var owner = actor(demo, 2000000000000201L, AccountRole.CONSUMER);
        var other = actor(demo, 2000000000000202L, AccountRole.CONSUMER);
        long orderId = 2000000000000301L;
        var snapshot = orders.detail(owner, orderId);
        assertThat(products.publicSku("M_3C_DEMO", sku).inStock()).isFalse();
        adjust(staff, sku, 2, "commerce-first");
        assertThat(products.publicSku("M_3C_DEMO", sku).inStock()).isTrue();
        assertThat(products.publicSku("M_ISOLATION_TEST", 2600000000000303L).inStock()).isFalse();
        assertThatThrownBy(() -> adjust(foreignStaff, sku, 1, "commerce-foreign"))
                .isInstanceOf(InventoryBizException.class);
        adjust(staff, sku, -2, "commerce-exhaust");
        assertThat(products.publicSku("M_3C_DEMO", sku).inStock()).isFalse();
        assertThat(inventory.query(staff, sku).getAvailableQty()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_idempotent WHERE status='SUCCESS'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT id FROM inventory WHERE merchant_id=? AND sku_id=?", Long.class, demo, sku))
                .isPositive().isNotEqualTo(2600000000000501L);

        jdbc.update("UPDATE product SET name='changed catalog',status='DISCONTINUED' WHERE id=2600000000000201");
        jdbc.update("UPDATE product_sku SET price=1.23,attributes_json='{}',status='DISCONTINUED' WHERE id=?", sku);
        cache.bump(demo);
        assertThat(orders.detail(owner, orderId)).isEqualTo(snapshot);
        assertThat(orders.detail(staff, orderId)).isEqualTo(snapshot);
        assertThat(shipments.getForActor(owner, orderId).status().name()).isEqualTo("DELIVERED");
        assertThat(shipments.getForActor(owner, 2000000000000302L).status().name()).isEqualTo("NOT_SHIPPED");
        for (Actor denied : new Actor[]{other, foreignStaff}) {
            assertThatThrownBy(() -> shipments.getForActor(denied, orderId))
                    .isInstanceOfSatisfying(AuthError.class, error -> assertThat(error.status()).isEqualTo(404));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE merchant_id=?", Integer.class, iso)).isZero();
    }

    private void adjust(Actor staff, long sku, int quantity, String key) {
        var dto = new InventoryAdjustDTO();
        dto.setSkuId(sku); dto.setAdjustQty(quantity); dto.setIdempotentKey(key);
        inventory.adjust(staff, dto);
    }
    private Actor actor(long merchant, long account, AccountRole role) {
        return new Actor(new AuthPrincipal(account, merchant, role), "commerce-integration");
    }
}
