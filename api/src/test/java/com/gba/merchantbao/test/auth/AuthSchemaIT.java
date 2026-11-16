package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.test.support.DockerRequired;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AuthSchemaIT {
    static { DockerRequired.check(); }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("auth_schema_test").withUsername("test").withPassword("test").withReuse(false);

    Connection connection;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed/nonprod").load().migrate();
    }

    @BeforeEach
    void begin() throws Exception {
        connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void consumerConstraintsEnforceTenantPhoneStatusAndDefaults() throws Exception {
        execute("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (11,1000000000000001,'+8613800000011')");
        rejected("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (12,1000000000000001,'+8613800000011')");
        execute("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (13,1000000000000002,'+8613800000011')");
        rejected("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (14,NULL,'+8613800000014')");
        rejected("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (15,999,'+8613800000015')");
        rejected("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (16,1000000000000001,NULL)");
        rejected("UPDATE consumer_account SET status='active' WHERE id=11");
        rejected("UPDATE consumer_account SET status='DELETED' WHERE id=11");
        try (var rows = connection.createStatement().executeQuery("SELECT * FROM consumer_account WHERE id=11")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("status")).isEqualTo("ACTIVE");
            assertThat(rows.getInt("version")).isZero();
            assertThat(rows.getTimestamp("created_at")).isNotNull();
            assertThat(rows.getTimestamp("updated_at")).isNotNull();
        }
        execute("UPDATE consumer_account SET status='DISABLED' WHERE id=11");
        rejected("INSERT INTO consumer_account(id,merchant_id,phone) VALUES (17,1000000000000001,'+8613800000011')");
    }

    @Test
    void staffUniquenessAndCreatorReferenceCannotCrossTenants() throws Exception {
        staff(21, 1000000000000001L, "staff_one", "+8613800000021", "NULL");
        staff(22, 1000000000000002L, "staff_one", "+8613800000021", "NULL");
        assertThatThrownBy(() -> staff(23, 1000000000000001L, "staff_one", "+8613800000023", "21"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> staff(24, 1000000000000001L, "staff_two", "+8613800000021", "21"))
                .isInstanceOf(SQLException.class);
        staff(25, 1000000000000001L, "staff_two", "+8613800000025", "21");
        assertThatThrownBy(() -> staff(26, 1000000000000002L, "staff_three", "+8613800000026", "21"))
                .isInstanceOf(SQLException.class);
        rejected("UPDATE merchant_staff_account SET created_by=22 WHERE id=25");
        rejected("UPDATE merchant_staff_account SET merchant_id=1000000000000002 WHERE id=25");
        rejected("UPDATE merchant_staff_account SET merchant_id=NULL WHERE id=21");
        rejected("UPDATE merchant_staff_account SET password_hash=NULL WHERE id=21");
        rejected("UPDATE merchant_staff_account SET status='DELETED' WHERE id=21");
        rejected("DELETE FROM merchant_staff_account WHERE id=21");
    }

    private void staff(long id, long merchant, String username, String phone, String creator) throws SQLException {
        execute("INSERT INTO merchant_staff_account(id,merchant_id,username,phone,password_hash,created_by) VALUES ("
                + id + "," + merchant + ",'" + username + "','" + phone + "','test-only-digest'," + creator + ")");
    }

    private void rejected(String sql) {
        assertThatThrownBy(() -> execute(sql)).isInstanceOf(SQLException.class);
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
