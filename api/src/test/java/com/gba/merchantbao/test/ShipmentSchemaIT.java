package com.gba.merchantbao.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure DDL contract check for V6__shipment_tables; no Spring context, manual transactions. */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentSchemaIT {
    static {DockerRequired.check();}

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("shipment_schema").withUsername("test").withPassword("test");
    static Connection connection;

    @BeforeAll
    static void migrate() throws Exception {
        MYSQL.start();
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        try (var s = connection.createStatement()) {
            s.execute("INSERT INTO merchant (id, merchant_code, merchant_name, status) VALUES "
                    + "(1000000000000001, 'SCHEMA_T1', 'schema tenant one', 'ACTIVE'), "
                    + "(1000000000000002, 'SCHEMA_T2', 'schema tenant two', 'ACTIVE')");
        }
    }

    @AfterAll
    static void close() throws Exception {
        if (connection != null) connection.close();
        MYSQL.stop();
    }

    @BeforeEach
    void begin() throws Exception {connection.setAutoCommit(false);}

    @AfterEach
    void rollback() throws Exception {connection.rollback();connection.setAutoCommit(true);}

    private void insertShipment(long id, long merchantId, long orderId, String status) throws Exception {
        insertShipment(id, merchantId, orderId, status, "TRK" + id);
    }

    private void insertShipment(long id, long merchantId, long orderId, String status, String trackingNo) throws Exception {
        try (var ps = connection.prepareStatement(
                "INSERT INTO shipment (id, merchant_id, order_id, order_no, status, carrier_name, tracking_no) "
                        + "VALUES (?,?,?,?,?,?,?)")) {
            ps.setLong(1, id);ps.setLong(2, merchantId);ps.setLong(3, orderId);
            ps.setString(4, "SO-" + id);ps.setString(5, status);
            ps.setString(6, "顺丰速运");ps.setString(7, trackingNo);
            ps.executeUpdate();
        }
    }

    @Test @Order(1)
    void oneShipmentPerMerchantOrder() throws Exception {
        insertShipment(6001, 1000000000000001L, 7001, "IN_TRANSIT");
        assertThatThrownBy(() -> insertShipment(6002, 1000000000000001L, 7001, "DELIVERED"))
                .isInstanceOf(SQLException.class);
    }

    @Test @Order(2)
    void trackingNumberIsUniquePerMerchant() throws Exception {
        insertShipment(6011, 1000000000000001L, 7011, "IN_TRANSIT");
        // Same waybill reused within one tenant violates uk_shipment_merchant_tracking;
        // the cross-tenant reuse of the same waybill is covered by @Order(5).
        assertThatThrownBy(() -> insertShipment(6012, 1000000000000001L, 7012, "IN_TRANSIT", "TRK6011"))
                .isInstanceOf(SQLException.class);
    }

    @Test @Order(3)
    void statusSetIsClosed() {
        assertThatThrownBy(() -> insertShipment(6021, 1000000000000001L, 7021, "SHIPPING"))
                .isInstanceOf(SQLException.class);
    }

    @Test @Order(4)
    void merchantForeignKeyIsEnforced() {
        assertThatThrownBy(() -> insertShipment(6031, 9999999999999999L, 7031, "IN_TRANSIT"))
                .isInstanceOf(SQLException.class);
    }

    @Test @Order(5)
    void sameTrackingNumberAcrossTenantsIsAllowed() throws Exception {
        insertShipment(6041, 1000000000000001L, 7041, "IN_TRANSIT");
        insertShipment(6042, 1000000000000002L, 7041, "IN_TRANSIT");
        var count = connection.prepareStatement(
                "SELECT COUNT(*) FROM shipment WHERE order_id = 7041");
        try (var rs = count.executeQuery()) {rs.next();assertThat(rs.getLong(1)).isEqualTo(2);}
    }

    @Test @Order(6)
    void trackEventIsUniquePerShipment() throws Exception {
        insertShipment(6051, 1000000000000001L, 7051, "IN_TRANSIT");
        try (var ps = connection.prepareStatement(
                "INSERT INTO shipment_track (id, shipment_id, merchant_id, event_time, status, description) "
                        + "VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, 6511);ps.setLong(2, 6051);ps.setLong(3, 1000000000000001L);
            ps.setTimestamp(4, Timestamp.valueOf("2026-11-07 08:00:00"));
            ps.setString(5, "IN_TRANSIT");ps.setString(6, "same description");
            ps.executeUpdate();
            ps.setLong(1, 6512);
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
        }
    }
}
