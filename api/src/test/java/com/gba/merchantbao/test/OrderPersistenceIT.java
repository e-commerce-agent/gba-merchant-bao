package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.order.exception.OrderError;
import com.gba.merchantbao.order.mapper.OrderItemQueryMapper;
import com.gba.merchantbao.order.mapper.OrderQueryMapper;
import com.gba.merchantbao.order.service.OrderQueryService;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderPersistenceIT {
    static { DockerRequired.check(); }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("order_mapper_test").withUsername("test").withPassword("test").withReuse(false);

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired TenantResolver tenants;
    @Autowired OrderQueryMapper orders;
    @Autowired OrderItemQueryMapper items;
    @Autowired OrderQueryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sessions;

    static Actor actor(long accountId, long merchantId, AccountRole role) {
        return new Actor(new AuthPrincipal(accountId, merchantId, role), "order-it-sid");
    }

    @Test
    @Sql({"/db/fixture/auth_accounts.sql", "/db/fixture/orders.sql"})
    void fixtureScopesStayInTenantAndOwnerBounds() {
        long demo = tenant("M_3C_DEMO"), isolated = tenant("M_ISOLATION_TEST");
        long consumer = 2000000000000201L, otherConsumer = 2000000000000202L;

        assertThat(orders.countByCustomer(demo, consumer)).isEqualTo(2);
        assertThat(orders.countByCustomer(demo, otherConsumer)).isEqualTo(1);
        assertThat(orders.countByMerchant(demo)).isEqualTo(3);
        assertThat(orders.countByMerchant(isolated)).isEqualTo(1);
        assertThat(orders.countByCustomer(isolated, consumer)).isZero();

        // Same order number in another tenant never leaks.
        assertThat(orders.findById(demo, 2000000000000304L)).isNull();
        assertThat(orders.findById(isolated, 2000000000000301L)).isNull();
        assertThat(orders.findOwnedById(demo, 2000000000000301L, otherConsumer)).isNull();
        assertThat(orders.findOwnedById(demo, 2000000000000301L, consumer)).isNotNull();

        // Items are scoped by merchant AND order, never by order number alone.
        assertThat(items.findByOrder(demo, 2000000000000301L)).hasSize(2);
        assertThat(items.findByOrder(isolated, 2000000000000301L)).isEmpty();
        assertThat(items.findByOrder(demo, 2000000000000304L)).isEmpty();
    }

    @Test
    @Sql({"/db/fixture/auth_accounts.sql", "/db/fixture/orders.sql"})
    void serviceReturnsSnapshotDetailAnd404ForOutOfScopeOrMissingOrders() {
        long demo = tenant("M_3C_DEMO"), isolated = tenant("M_ISOLATION_TEST");
        var consumer = actor(2000000000000201L, demo, AccountRole.CONSUMER);
        var other = actor(2000000000000202L, demo, AccountRole.CONSUMER);
        var staff = actor(2000000000000101L, demo, AccountRole.MERCHANT_STAFF);
        var foreignStaff = actor(2000000000000102L, isolated, AccountRole.MERCHANT_STAFF);

        var page = service.list(consumer, 1, 10);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).allSatisfy(r -> assertThat(r.orderNo()).isIn("DEV-2026-0901", "DEV-2026-0902"));

        var staffPage = service.list(staff, 1, 10);
        assertThat(staffPage.total()).isEqualTo(3);
        var foreignPage = service.list(foreignStaff, 1, 10);
        assertThat(foreignPage.total()).isEqualTo(1);
        assertThat(foreignPage.records().get(0).orderNo()).isEqualTo("DEV-2026-0901");

        var detail = service.detail(consumer, 2000000000000301L);
        assertThat(detail.orderNo()).isEqualTo("DEV-2026-0901");
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().get(0).productName()).isEqualTo("65W 双口充电器");
        assertThat(detail.items().get(0).skuAttributes()).contains("USB-C");
        assertThat(detail.items().stream().map(i -> i.amount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualTo(detail.totalAmount());

        assertThatThrownBy(() -> service.detail(other, 2000000000000301L)).isInstanceOf(OrderError.class)
                .satisfies(e -> assertThat(((OrderError) e).status()).isEqualTo(404));
        assertThatThrownBy(() -> service.detail(consumer, 2000000000000304L)).isInstanceOf(OrderError.class);
        assertThatThrownBy(() -> service.detail(staff, 2000000000000304L)).isInstanceOf(OrderError.class);
        assertThatThrownBy(() -> service.detail(consumer, 999999999L)).isInstanceOf(OrderError.class);
    }

    @Test
    @Sql({"/db/fixture/auth_accounts.sql", "/db/fixture/orders.sql"})
    void paginationHonoursLimitOffsetAndIsExplainable() throws Exception {
        long demo = tenant("M_3C_DEMO");
        var consumer = actor(2000000000000201L, demo, AccountRole.CONSUMER);
        var pageOne = service.list(consumer, 1, 1);
        var pageTwo = service.list(consumer, 2, 1);
        assertThat(pageOne.total()).isEqualTo(2);
        assertThat(pageOne.records()).hasSize(1);
        assertThat(pageTwo.records()).hasSize(1);
        assertThat(pageOne.records().get(0).orderId()).isNotEqualTo(pageTwo.records().get(0).orderId());
        assertThat(service.list(consumer, 3, 1).records()).isEmpty();
        assertThat(service.list(consumer, 1, 100).total()).isEqualTo(2);

        assertIndex(OrderQueryMapper.class, "findByCustomer",
                Map.of("merchantId", demo, "customerId", 2000000000000201L, "limit", 1, "offset", 0),
                "idx_trade_order_merchant_customer_placed");
        assertIndex(OrderQueryMapper.class, "findById",
                Map.of("merchantId", demo, "orderId", 2000000000000301L), "PRIMARY", "uk_trade_order_merchant_id");
        assertIndex(OrderItemQueryMapper.class, "findByOrder",
                Map.of("merchantId", demo, "orderId", 2000000000000301L), "idx_trade_order_item_merchant_order");
    }

    @Test
    @Sql("/db/fixture/auth_accounts.sql")
    void itemRowsCannotEscapeTheirOrderOrTenantByForeignKeys() {
        long demo = tenant("M_3C_DEMO"), isolated = tenant("M_ISOLATION_TEST");
        long orderId = 2000000000000901L, itemId = 2000000000000911L, customerId = 2000000000000201L;
        jdbc.update("INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, pay_status, total_amount, currency) " +
                        "VALUES (?, ?, 'ORD-FK', ?, 'COMPLETED', 'PAID', 10.00, 'CNY')",
                orderId, demo, customerId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO trade_order_item (id, merchant_id, order_id, product_id, sku_id, " +
                        "product_name_snapshot, unit_price, quantity, amount) VALUES (?, ?, ?, 1, 1, 'x', 10.00, 1, 10.00)",
                itemId, isolated, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO trade_order (id, merchant_id, order_no, customer_id, status, " +
                        "pay_status, total_amount, currency) VALUES (?, ?, 'ORD-FK2', ?, 'COMPLETED', 'PAID', 10.00, 'CNY')",
                2000000000000902L, demo, 2000000000000999L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE trade_order SET status='SHIPPING' WHERE id=?", orderId))
                .isInstanceOf(org.springframework.dao.NonTransientDataAccessException.class)
                .hasMessageContaining("ck_trade_order_status");
        assertThat(orders.findById(demo, orderId).status()).isEqualTo("COMPLETED");
    }

    // EXPLAIN the actual bound Mapper SQL, not a separately maintained imitation query.
    private void assertIndex(Class<?> mapper, String method, Map<String, Object> parameters, String... indexes) throws Exception {
        var bound = sessions.getConfiguration().getMappedStatement(mapper.getName() + "." + method).getBoundSql(parameters);
        var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource());
        try (var statement = connection.prepareStatement("EXPLAIN " + bound.getSql())) {
            int i = 1;
            for (var mapping : bound.getParameterMappings()) statement.setObject(i++, parameters.get(mapping.getProperty()));
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("key")).isIn(indexes);
                assertThat(rows.getString("type")).isIn("const", "ref", "eq_ref", "range");
            }
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, jdbc.getDataSource());
        }
    }

    private long tenant(String code) { return tenants.resolveActive(code).orElseThrow().id(); }
}
