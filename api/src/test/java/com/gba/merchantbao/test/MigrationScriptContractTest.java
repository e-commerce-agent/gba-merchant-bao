package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationScriptContractTest {

    @Test
    void frozenMigrationsAndSeedsKeepTheirRecordedDigests() throws Exception {
        var expected = Map.ofEntries(
                Map.entry("V1__legacy_schema_baseline.sql", "694EE44ECC087FA5246AB4A11BD7A9A9469675C5A3F7A10D7C23866ACE573107"),
                Map.entry("V2__merchant_tenant_root.sql", "1131FDCBBD166CF3630D5B52BF8473AE3DEF7D29DAAC8175A56003E7B09B40C5"),
                Map.entry("V3__merchant_currency_compatibility.sql", "59EFC696234060CB2121D3E7403E1B10BB917F8371E84286F44CE55B86CA45B1"),
                Map.entry("V4__auth_accounts.sql", "87B19CB89EA045F203CF3DE2DF2E0532365D1385AAA16A137674B558AB0B629A"),
                Map.entry("V5__agent_conversation.sql", "DA150835D15584F8CC0C1E0DAB8CA2504FEA308D8820720DB0CD05B63A143408"),
                Map.entry("R__010_local_test_tenants.sql", "B41D0F607CBA61ED30B091E114CCF6D6449BC0D2034CB778D72CBE1927F9E684"),
                Map.entry("R__020_auth_demo_accounts.sql", "B15237288372B9300889A2FB2E58C74CB9B9A772B2E6420C0A3CDA8D8213F68B"),
                Map.entry("R__030_chat_demo.sql", "9B4E9E907F70DFE216B107F894E8663BD1BFADE000D520D617A9960B5C7A4B34"));
        for (var entry : expected.entrySet()) {
            String resource = entry.getKey().startsWith("V") ? "db/migration/" + entry.getKey()
                    : entry.getKey().startsWith("R__010") ? "db/seed/nonprod/" + entry.getKey() : "db/devdata/" + entry.getKey();
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(stream).as(resource).isNotNull();
                assertThat(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()))
                        .containsExactly(java.util.HexFormat.of().parseHex(entry.getValue()));
            }
        }
    }

    @Test
    void commerceMigrationVersionsAreUniqueDdlOnlyAndOrdered() throws IOException {
        var migrationDirectory = Path.of("src/main/resources/db/migration");
        var migrations = java.nio.file.Files.list(migrationDirectory)
                .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                .map(path -> path.getFileName().toString()).sorted().toList();
        assertThat(migrations).contains("V6__product_module.sql", "V7__inventory_tables.sql",
                "V8__order_tables.sql", "V9__shipment_tables.sql");
        assertThat(migrations).noneMatch(name -> name.matches("V(10|[1-9][0-9]+)__.*"));
        assertThat(migrations.stream().map(name -> name.substring(1, name.indexOf("__"))).distinct().count())
                .isEqualTo(migrations.size());
        for (int i = 0; i < migrations.size(); i++) {
            String sql = java.nio.file.Files.readString(migrationDirectory.resolve(migrations.get(i)));
            String executable = sql.lines().filter(line -> !line.trim().startsWith("--")).reduce("", (a, b) -> a + b + "\n");
            assertThat(executable).as(migrations.get(i)).doesNotContainPattern("(?im)^\\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE)\\b");
        }
    }

    @Test
    void authMigrationIsDdlOnlyAndAuthFixturesAreNotFlywaySeeds() throws IOException {
        String migration = resource("db/migration/V4__auth_accounts.sql");
        assertThat(migration).doesNotContainPattern("(?im)^\\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP)\\b");
        assertThat(migration).contains("CREATE TABLE `consumer_account`", "CREATE TABLE `merchant_staff_account`");
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources/db/seed"))) {
            for (var path : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                assertThat(java.nio.file.Files.readString(path)).doesNotContain("consumer_account", "merchant_staff_account", "fixture_staff");
            }
        }
        String fixture = resource("db/fixture/auth_accounts.sql");
        assertThat(fixture).contains("merchant_code = 'M_3C_DEMO'", "merchant_code = 'M_ISOLATION_TEST'", "$2b$12$");
        assertThat(resource("application-test.yml")).doesNotContain("db/fixture", "db/devdata");
        assertThat(resource("application-prod.yml")).doesNotContain("db/fixture", "db/seed/nonprod", "db/devdata");
    }

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
        String seed = resource("db/seed/nonprod/R__010_local_test_tenants.sql");
        assertThat(seed).contains("M_3C_DEMO", "M_ISOLATION_TEST", "WHERE NOT EXISTS");
        assertThat(resource("application-prod.yml")).doesNotContain("db/devdata");
        assertThat(resource("application-test.yml")).contains("classpath:db/migration,classpath:db/seed/common,classpath:db/seed/nonprod");
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
