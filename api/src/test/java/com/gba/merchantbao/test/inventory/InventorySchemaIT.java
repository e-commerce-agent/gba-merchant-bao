package com.gba.merchantbao.test.inventory;

import com.gba.merchantbao.test.support.DockerRequired;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** Isolated DDL verification; no dev seed, application startup or local database access. */
@Testcontainers
class InventorySchemaIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("inventory_schema").withUsername("test").withPassword("test");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void allInventoryPrimaryKeysAreRequiredBigintsWithoutGeneratedDefaults() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var query = connection.prepareStatement("""
                     SELECT DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_KEY
                     FROM information_schema.columns
                     WHERE table_schema=DATABASE() AND table_name=? AND column_name='id'
                     """)) {
            for (String table : List.of("inventory", "inventory_transaction", "inventory_idempotent")) {
                query.setString(1, table);
                try (var row = query.executeQuery()) {
                    assertThat(row.next()).as(table).isTrue();
                    assertThat(row.getString("DATA_TYPE")).isEqualTo("bigint");
                    assertThat(row.getString("IS_NULLABLE")).isEqualTo("NO");
                    assertThat(row.getString("COLUMN_DEFAULT")).isNull();
                    assertThat(row.getString("EXTRA")).doesNotContain("auto_increment");
                    assertThat(row.getString("COLUMN_KEY")).isEqualTo("PRI");
                }
            }
        }
    }

    @Test
    void missingPrimaryKeyIsRejectedRatherThanGenerated() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO inventory_idempotent (idempotent_key, merchant_id, biz_type, status)
                    VALUES ('schema-missing-id', 1, 'ADJUST', 'PROCESSING')
                    """))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(1364));
        }
    }
}
