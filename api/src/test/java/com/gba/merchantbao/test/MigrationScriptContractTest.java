package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationScriptContractTest {

    @Test
    void legacyBaselineContainsSchemaOnlyStatements() throws IOException {
        String baseline = resource("db/migration/V1__legacy_schema_baseline.sql");
        String executableSql = baseline.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n");

        assertThat(executableSql).doesNotMatch("(?is).*\\b(drop|insert|update|delete)\\b.*");
        assertThat(executableSql).contains("CREATE TABLE `address_book`");
        assertThat(executableSql).contains("CREATE TABLE `user`");
    }

    @Test
    void tenantFixturesAreIdempotentAndProductionDoesNotLoadThem() throws IOException {
        String seed = resource("db/devdata/R__local_test_tenants.sql");
        assertThat(seed).contains("M_3C_DEMO", "M_ISOLATION_TEST", "ON DUPLICATE KEY UPDATE");
        assertThat(resource("application-prod.yml")).doesNotContain("db/devdata");
        assertThat(resource("application-local.yml")).contains("classpath:db/migration,classpath:db/devdata");
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
