package com.gba.merchantbao.test;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
                .baselineVersion("1")
                .load();

        flyway.migrate();
        assertCurrentVersionEight(flyway);
        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             ResultSet tables = connection.createStatement().executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema='merchant_test' AND table_name IN ('consumer_account','merchant_staff_account') "
                             + "ORDER BY table_name")) {
            assertThat(tables.next()).isTrue();
            assertThat(tables.getString(1)).isEqualTo("consumer_account");
            assertThat(tables.next()).isTrue();
            assertThat(tables.getString(1)).isEqualTo("merchant_staff_account");
            assertThat(tables.next()).isFalse();
        }
        assertThat(jdbc().queryForList("SELECT id FROM consumer_account")).isEmpty();
        assertThat(jdbc().queryForList("SELECT id FROM merchant_staff_account")).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void testSeedsAreIdempotentAndUseStableTenantCodes() throws Exception {
        Flyway flyway = migration("classpath:db/migration", "classpath:db/seed/nonprod");
        flyway.migrate();
        assertCurrentVersionEight(flyway);
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/seed/nonprod/R__010_local_test_tenants.sql"));
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
        flyway.migrate();
        assertCurrentVersionEight(flyway);
        try (Connection connection = connection();
             ResultSet rows = connection.createStatement().executeQuery(
                     "SELECT name FROM category WHERE id = 987654")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("legacy-preserved");
        }
    }

    @Test
    void registeredVersionThreeUpgradesOnlyStructureAndRepeatedMigratePreservesDataAndHistory() {
        var v3 = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration").target("3").load();
        v3.migrate();
        assertThat(v3.info().current().getVersion().getVersion()).isEqualTo("3");
        var oldChecksums = Arrays.stream(v3.info().applied()).collect(Collectors.toMap(
                info -> info.getVersion().getVersion(), info -> info.getChecksum()));
        var jdbc = jdbc();
        jdbc.update("INSERT INTO merchant(id,merchant_code,merchant_name,version) VALUES (9901,'M_UPGRADE_PROBE','preserve me',7)");
        jdbc.update("INSERT INTO category(id,type,name,sort,status) VALUES (9902,1,'upgrade-preserved',1,1)");
        var merchantBefore = jdbc.queryForList("SELECT * FROM merchant WHERE id=9901");
        var legacyBefore = jdbc.queryForList("SELECT * FROM category WHERE id=9902");

        var latest = migration("classpath:db/migration");
        // Upgrading from a V3 target applies V4 (auth), V5 (agent conversation), V6 (shipment), V7 (order) and V8 (inventory).
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(5);
        assertCurrentVersionEight(latest);
        for (var info : latest.info().applied()) {
            if (oldChecksums.containsKey(info.getVersion().getVersion())) {
                assertThat(info.getChecksum()).isEqualTo(oldChecksums.get(info.getVersion().getVersion()));
            }
        }
        assertThat(jdbc.queryForList("SELECT * FROM merchant WHERE id=9901")).isEqualTo(merchantBefore);
        assertThat(jdbc.queryForList("SELECT * FROM category WHERE id=9902")).isEqualTo(legacyBefore);
        assertThat(jdbc.queryForList("SELECT id FROM consumer_account")).isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM merchant_staff_account")).isEmpty();
        jdbc.update("INSERT INTO consumer_account(id,merchant_id,phone,display_name,status,version) "
                + "VALUES (9903,9901,'+8613900000501','preserve consumer','DISABLED',3)");
        jdbc.update("INSERT INTO merchant_staff_account(id,merchant_id,username,phone,password_hash,status,version) "
                + "VALUES (9904,9901,'upgrade_staff','+8613900000502','test-only-unchanged-digest','DISABLED',5)");
        var consumerBefore = jdbc.queryForList("SELECT * FROM consumer_account WHERE id=9903");
        var staffBefore = jdbc.queryForList("SELECT * FROM merchant_staff_account WHERE id=9904");
        var historyBefore = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(latest.migrate().migrationsExecuted).isZero();
        latest.validate();
        assertThat(jdbc.queryForList("SELECT * FROM consumer_account WHERE id=9903")).isEqualTo(consumerBefore);
        assertThat(jdbc.queryForList("SELECT * FROM merchant_staff_account WHERE id=9904")).isEqualTo(staffBefore);
        assertThat(jdbc.queryForList("SELECT * FROM merchant WHERE id=9901")).isEqualTo(merchantBefore);
        assertThat(jdbc.queryForList("SELECT * FROM category WHERE id=9902")).isEqualTo(legacyBefore);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(historyBefore);
    }

    private void assertCurrentVersionEight(Flyway flyway) {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).allSatisfy(info ->
                assertThat(info.getState()).isIn(MigrationState.SUCCESS, MigrationState.BASELINE));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
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
