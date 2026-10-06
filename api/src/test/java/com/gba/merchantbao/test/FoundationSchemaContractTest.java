package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the foundation contracts without requiring a running database. */
class FoundationSchemaContractTest {

    @Test
    void tenantRootUsesStableCodeAndTenantSafeIndexes() throws IOException {
        String merchant = resource("db/migration/V2__merchant_tenant_root.sql");
        assertThat(merchant).contains("merchant_code", "NOT NULL", "uk_merchant_code");
        assertThat(merchant).contains("idx_merchant_status");
        assertThat(resource("db/devdata/R__local_test_tenants.sql"))
                .contains("M_3C_DEMO", "M_ISOLATION_TEST", "ON DUPLICATE KEY UPDATE");
    }

    @Test
    void publishedMigrationChecksumsRemainImmutable() throws Exception {
        Map<String, String> expected = Map.of(
                "db/migration/V1__legacy_schema_baseline.sql", "694ee44ecc087fa5246ab4a11bd7a9a9469675c5a3f7a10d7c23866ace573107",
                "db/migration/V2__merchant_tenant_root.sql", "4a935fe633de73bd9f9e7dfd41015a13ecdbf146b0c5da6f228c1ab3428e7b49",
                "db/migration/V3__merchant_currency_compatibility.sql", "bf1aa83d261bdc70b3bd16f4b032b07a03ba1f3eaf6ecabefeed77e76ab54567"
        );
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertThat(sha256(resource(entry.getKey()))).isEqualTo(entry.getValue());
        }
    }

    @Test
    void foundationPackageDoesNotReachLegacyPersistencePackages() throws IOException {
        String controller = source("com/gba/merchantbao/api/FoundationController.java");
        String handler = source("com/gba/merchantbao/api/FoundationExceptionHandler.java");
        assertThat(controller + handler)
                .doesNotContain("com.gba.merchantbao.legacy.mapper", "com.gba.merchantbao.legacy.entity");
    }

    @Test
    void tenantBoundaryRulesAreDocumentedForWritesAndDeletes() throws IOException {
        String baseline = file("docs/initialization/2026-10-06-产出文档/基础迁移基线.md");
        assertThat(baseline).contains("非空 `merchant_id`", "更新和删除 SQL", "服务端身份解析");
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String source(String path) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java", path), StandardCharsets.UTF_8);
    }

    private String file(String path) throws IOException {
        java.nio.file.Path direct = java.nio.file.Path.of(path);
        java.nio.file.Path moduleRelative = java.nio.file.Path.of("..", path);
        return java.nio.file.Files.readString(java.nio.file.Files.exists(direct) ? direct : moduleRelative, StandardCharsets.UTF_8);
    }

    private String sha256(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
