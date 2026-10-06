package com.gba.merchantbao.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationIT {

    static {
        DockerRequired.check();
    }

    @Container
    final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("merchant_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    @Test
    void freshDatabaseMigratesStructureWithoutDemoRows() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .outOfOrder(false)
                .baselineOnMigrate(false)
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             ResultSet tables = connection.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema='merchant_test' AND table_name <> 'flyway_schema_history'")) {
            assertThat(tables.next()).isTrue();
            assertThat(tables.getInt(1)).isEqualTo(12);
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void testSeedsAreIdempotentAndUseStableTenantCodes() throws Exception {
        Flyway flyway = migration("classpath:db/migration", "classpath:db/devdata");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/devdata/R__local_test_tenants.sql"));
            try (ResultSet tenants = connection.createStatement().executeQuery(
                    "SELECT merchant_code FROM merchant ORDER BY merchant_code")) {
                assertThat(tenants.next()).isTrue();
                assertThat(tenants.getString(1)).isEqualTo("M_3C_DEMO");
                assertThat(tenants.next()).isTrue();
                assertThat(tenants.getString(1)).isEqualTo("M_ISOLATION_TEST");
                assertThat(tenants.next()).isFalse();
            }
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void existingLegacySchemaRequiresExplicitBaselineAndPreservesRows() throws Exception {
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__legacy_schema_baseline.sql"));
            connection.createStatement().executeUpdate(
                    "INSERT INTO category (id, type, name, sort, status) "
                            + "VALUES (987654, 1, 'legacy-preserved', 1, 1)");
        }
        Flyway flyway = migration("classpath:db/migration");
        org.assertj.core.api.Assertions.assertThatThrownBy(flyway::migrate)
                .isInstanceOf(org.flywaydb.core.api.FlywayException.class)
                .hasMessageContaining("non-empty schema");
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        try (Connection connection = connection();
             ResultSet rows = connection.createStatement().executeQuery(
                     "SELECT name FROM category WHERE id = 987654")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("legacy-preserved");
        }
    }

    private Flyway migration(String... locations) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations(locations)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .baselineVersion("1")
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }
}
