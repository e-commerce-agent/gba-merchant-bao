package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.product.entity.ProductCategoryRow;
import com.gba.merchantbao.product.entity.ProductRow;
import com.gba.merchantbao.product.entity.ProductSkuRow;
import com.gba.merchantbao.product.exception.ProductError;
import com.gba.merchantbao.product.mapper.ProductCategoryMapper;
import com.gba.merchantbao.product.mapper.ProductSkuMapper;
import com.gba.merchantbao.product.service.ProductCommandService;
import com.gba.merchantbao.product.service.ProductQueryService;
import com.gba.merchantbao.product.vo.ProductViews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@ActiveProfiles("test") @Testcontainers @Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductPersistenceIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("product_mapper").withUsername("test").withPassword("test").withReuse(false);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl); r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired ProductCategoryMapper categories;
    @Autowired com.gba.merchantbao.product.mapper.ProductMapper products;
    @Autowired ProductSkuMapper skus;
    @Autowired com.gba.merchantbao.product.mapper.ProductAttributeMapper attributes;
    @Autowired ProductQueryService queries;
    @Autowired ProductCommandService commands;
    @Autowired JdbcTemplate jdbc;
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 0, 0, 0, 123_000_000);

    long merchantId(String code) { return jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code=?", Long.class, code); }

    Actor staffOf(String code) { return new Actor(new AuthPrincipal(2000000000000101L, merchantId(code), AccountRole.MERCHANT_STAFF), "it-session"); }
    Actor consumerOf(String code) { return new Actor(new AuthPrincipal(2000000000000201L, merchantId(code), AccountRole.CONSUMER), "it-session"); }

    ProductRow product(long id, long merchantId, String code, String status) {
        return new ProductRow(id, merchantId, null, code, "测试商品 " + id, "ItBrand", null, status, NOW, NOW);
    }
    ProductSkuRow sku(long id, long merchantId, long productId, String code, BigDecimal price, String status) {
        return new ProductSkuRow(id, merchantId, productId, code, "测试 SKU " + id, price, "{\"规格\":\"标准\"}", status, NOW, NOW);
    }

    @Test void catalogRowsAreScopedByMerchantAndVisibilityFiltersApply() {
        long demo = merchantId("M_3C_DEMO");
        long iso = merchantId("M_ISOLATION_TEST");
        var category = new ProductCategoryRow(3000000000000101L, demo, null, "IT 分类", 1, "ACTIVE", NOW, NOW);
        assertThat(categories.insert(category)).isOne();
        assertThat(categories.insert(new ProductCategoryRow(3000000000000102L, iso, null, "IT 分类", 1, "ACTIVE", NOW, NOW))).isOne();
        assertThat(categories.nameTaken(demo, null, "IT 分类", null)).isTrue();
        assertThat(categories.nameTaken(iso, null, "IT 分类", null)).isTrue();
        assertThat(categories.nameTaken(demo, null, "别的名字", null)).isFalse();
        assertThat(categories.nameTaken(demo, null, "IT 分类", 3000000000000101L)).isFalse();

        assertThat(products.insert(product(3000000000000201L, demo, "IT-PROD-1", "PUBLISHED"))).isOne();
        assertThat(products.insert(product(3000000000000202L, demo, "IT-PROD-2", "UNPUBLISHED"))).isOne();
        assertThat(products.insert(product(3000000000000203L, iso, "IT-PROD-1", "PUBLISHED"))).isOne();

        assertThat(skus.insert(sku(3000000000000301L, demo, 3000000000000201L, "IT-SKU-1", new BigDecimal("99.00"), "ACTIVE"))).isOne();
        assertThat(skus.insert(sku(3000000000000302L, demo, 3000000000000202L, "IT-SKU-2", new BigDecimal("59.00"), "ACTIVE"))).isOne();
        assertThat(skus.codeTaken(demo, "IT-SKU-1", null)).isTrue();
        assertThat(skus.codeTaken(demo, "IT-SKU-1", 3000000000000301L)).isFalse();
        assertThat(skus.codeTaken(iso, "IT-SKU-1", null)).isFalse();

        for (long wrong : new long[]{iso}) {
            assertThat(products.find(wrong, 3000000000000201L)).isNull();
            assertThat(skus.find(wrong, 3000000000000301L)).isNull();
            assertThat(products.page(wrong, null, null, null, null, 10)).extracting(ProductRow::id)
                    .containsExactly(3000000000000203L);
            assertThat(skus.listForProduct(wrong, 3000000000000201L, null)).isEmpty();
        }
        assertThat(products.page(demo, "PUBLISHED", null, null, null, 10)).extracting(ProductRow::id)
                .containsExactly(3000000000000201L);
        assertThat(products.page(demo, null, null, null, null, 10)).extracting(ProductRow::id)
                .containsExactly(3000000000000202L, 3000000000000201L);
        assertThat(products.page(demo, null, null, NOW, 3000000000000202L, 10)).extracting(ProductRow::id)
                .containsExactly(3000000000000201L);
    }

    @Test void publishTransitionsAreGuardedAndDiscontinueIsIdempotent() {
        long demo = merchantId("M_3C_DEMO");
        var actor = staffOf("M_3C_DEMO");
        products.insert(product(3000000000000211L, demo, "IT-PROD-11", "DRAFT"));
        skus.insert(sku(3000000000000311L, demo, 3000000000000211L, "IT-SKU-11", new BigDecimal("10.00"), "ACTIVE"));

        assertThatThrownBy(() -> commands.unpublish(actor, 3000000000000211L)).isInstanceOf(ProductError.class);
        assertThat(commands.publish(actor, 3000000000000211L).status()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> commands.publish(actor, 3000000000000211L)).isInstanceOf(ProductError.class);
        assertThat(commands.unpublish(actor, 3000000000000211L).status()).isEqualTo("UNPUBLISHED");
        assertThat(commands.discontinue(actor, 3000000000000211L).status()).isEqualTo("DISCONTINUED");
        assertThat(commands.discontinue(actor, 3000000000000211L).status()).isEqualTo("DISCONTINUED");
        assertThat(skus.find(demo, 3000000000000311L).status()).isEqualTo("DISCONTINUED");
        assertThatThrownBy(() -> commands.updateProduct(actor, 3000000000000211L,
                update("{\"name\":\"x\"}"))).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> commands.publish(actor, 999999L)).isInstanceOf(ProductError.class);

        var consumer = consumerOf("M_3C_DEMO");
        assertThatThrownBy(() -> commands.publish(consumer, 3000000000000201L)).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> commands.createProduct(consumer, create("{\"productCode\":\"IT-X\",\"name\":\"n\"}")))
                .isInstanceOf(ProductError.class);
    }

    @Test @Sql({"/db/fixture/auth_accounts.sql", "/db/fixture/product_catalog.sql"})
    void fixturesExposeRoleVisibilityTenantIsolationAndAgentFacts() {
        var publicPage = queries.publicPage("M_3C_DEMO", null, null, 20);
        assertThat(publicPage.items()).extracting(ProductViews.PublicProductView::productId)
                .containsExactly(new com.gba.merchantbao.common.api.ApiId(2600000000000201L));
        assertThat(publicPage.items().getFirst().skus()).extracting(ProductViews.PublicSkuView::skuCode)
                .containsExactly("FIX-SKU-1");
        assertThat(publicPage.items().getFirst().attributes()).containsEntry("兼容机型", "iPhone 15;小米 14");

        assertThatThrownBy(() -> queries.publicPage("M_NO_SUCH_STORE", null, null, 20)).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> queries.publicProduct("M_3C_DEMO", 2600000000000202L)).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> queries.publicSku("M_3C_DEMO", 2600000000000302L)).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> queries.publicProduct("M_ISOLATION_TEST", 2600000000000201L)).isInstanceOf(ProductError.class);

        var consumer = consumerOf("M_3C_DEMO");
        assertThat(queries.staffOrConsumerPage(consumer, null, null, 20).items())
                .extracting(ProductViews.ProductView::productId)
                .containsExactly(new com.gba.merchantbao.common.api.ApiId(2600000000000201L));
        assertThatThrownBy(() -> queries.staffOrConsumerProduct(consumer, 2600000000000202L)).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> queries.staffOrConsumerProduct(consumer, 2600000000000203L)).isInstanceOf(ProductError.class);
        assertThatThrownBy(() -> queries.staffOrConsumerSku(consumer, 2600000000000303L)).isInstanceOf(ProductError.class);

        var staff = staffOf("M_3C_DEMO");
        assertThat(queries.staffOrConsumerPage(staff, null, null, 20).items()).extracting(ProductViews.ProductView::productId)
                .containsExactly(new com.gba.merchantbao.common.api.ApiId(2600000000000202L),
                        new com.gba.merchantbao.common.api.ApiId(2600000000000201L));
        assertThat(queries.staffCategories(staff)).extracting(ProductViews.CategoryView::categoryId)
                .containsExactly(new com.gba.merchantbao.common.api.ApiId(2600000000000101L));
        assertThat(queries.staffCategories(staff).getFirst().children()).isEmpty();

        long iso = merchantId("M_ISOLATION_TEST");
        assertThat(queries.findVisibleProduct(iso, 2600000000000201L)).isNull();
        assertThat(queries.findVisibleProduct(merchantId("M_3C_DEMO"), 2600000000000202L)).isNull();
        var fact = queries.findVisibleProduct(merchantId("M_3C_DEMO"), 2600000000000201L);
        assertThat(fact).isNotNull();
        assertThat(fact.skus()).hasSize(1);
        assertThat(fact.skus().getFirst().compatibilityAttributes()).containsEntry("兼容机型", "iPhone 15;小米 14");
        assertThat(queries.findVisibleSku(merchantId("M_3C_DEMO"), 2600000000000302L)).isNull();
        assertThat(queries.listVisibleProducts(merchantId("M_3C_DEMO"), 10)).hasSize(1);
    }

    private static String json(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    private static com.gba.merchantbao.product.dto.ProductRequests.ProductUpdate update(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json(body), com.gba.merchantbao.product.dto.ProductRequests.ProductUpdate.class);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static com.gba.merchantbao.product.dto.ProductRequests.ProductCreate create(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json(body), com.gba.merchantbao.product.dto.ProductRequests.ProductCreate.class);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
