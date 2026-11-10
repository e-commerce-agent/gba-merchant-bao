package com.gba.merchantbao.test;

import com.gba.merchantbao.maintenance.DevSeedUpgrade;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DevSeedMigrationIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("seed_control").withUsername("test").withPassword("test").withReuse(false);
    @TempDir Path files;
    String url;
    String database;
    JdbcTemplate jdbc;
    DevSeedUpgrade tool;

    @BeforeEach void isolatedDatabase() throws Exception {
        database = "gba_seed_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()); var s = c.createStatement()) {
            s.execute("CREATE DATABASE `" + database + "`");
        }
        url = "jdbc:mysql://127.0.0.1:" + MYSQL.getMappedPort(3306) + "/" + database + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "root", MYSQL.getPassword()));
        tool = new DevSeedUpgrade(url, "root", MYSQL.getPassword(), database);
    }

    @Test void freshOrderAndUnchangedMigrateHaveExactlyOneExecutionPerSeed() {
        // V1-V8 (8) + R__010/R__020/R__030/R__040/R__050 (5) = 13.
        assertThat(tool.flyway().migrate().migrationsExecuted).isEqualTo(13);
        assertThat(jdbc.queryForList("SELECT script FROM flyway_schema_history ORDER BY installed_rank", String.class))
                .endsWith("R__010_local_test_tenants.sql", "R__020_auth_demo_accounts.sql", "R__030_chat_demo.sql",
                        "R__040_shipment_demo_data.sql", "R__050_order_demo_orders.sql");
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        var tenants = jdbc.queryForList("SELECT * FROM merchant ORDER BY id");
        assertThat(tool.flyway().migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT * FROM merchant ORDER BY id")).isEqualTo(tenants);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account", Integer.class)).isEqualTo(3);
    }

    @Test void chatExplicitReexecutionPreservesManualChangesAndRepairsOnlyMissingStableRows() throws Exception {
        tool.flyway().migrate();
        var original = jdbc.queryForMap("SELECT * FROM chat_message WHERE id=2300000000000002");
        assertThat(original.get("content")).isEqualTo("这是开发演示回复，不代表真实模型调用。");
        jdbc.update("UPDATE chat_session SET title='人工标题',status='ARCHIVED',archived_at='2026-11-08',archived_by=owner_account_id WHERE id=2200000000000001");
        jdbc.update("UPDATE chat_message SET content='人工正文',input_tokens=123 WHERE id=2300000000000002");
        var before = tool.snapshot();
        execute("db/devdata/R__030_chat_demo.sql"); execute("db/devdata/R__030_chat_demo.sql");
        tool.assertPreserved(before);
        assertThat(jdbc.queryForObject("SELECT title FROM chat_session WHERE id=2200000000000001",String.class)).isEqualTo("人工标题");
        assertThat(jdbc.queryForObject("SELECT content FROM chat_message WHERE id=2300000000000002",String.class)).isEqualTo("人工正文");
        jdbc.update("DELETE FROM chat_message WHERE id=2300000000000004");
        assertThat(tool.flyway().migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM chat_message WHERE id=2300000000000004")).isEmpty();
        execute("db/devdata/R__030_chat_demo.sql");
        assertThat(jdbc.queryForObject("SELECT session_id FROM chat_message WHERE id=2300000000000004",Long.class)).isEqualTo(2200000000000002L);
    }

    @Test void chatDependenciesAndIdentityConflictsFailBeforeBusinessWrites() throws Exception {
        structure(); execute("db/seed/nonprod/R__010_local_test_tenants.sql");
        execute("db/devdata/R__020_auth_demo_accounts.sql");
        jdbc.update("DELETE FROM consumer_account WHERE id=2100000000000202");
        var missing = tool.snapshot();
        assertThatThrownBy(() -> execute("db/devdata/R__030_chat_demo.sql")).hasStackTraceContaining("dev_seed_chat_missing_owner");
        assertThat(tool.snapshot()).isEqualTo(missing);
        execute("db/devdata/R__020_auth_demo_accounts.sql");
        jdbc.update("INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,created_at,updated_at) VALUES(2200000000000001,1000000000000002,2100000000000201,'CONSUMER','2026-11-08','2026-11-08')");
        var conflict = tool.snapshot();
        assertThatThrownBy(() -> execute("db/devdata/R__030_chat_demo.sql")).hasStackTraceContaining("dev_seed_chat_session_identity_conflict");
        assertThat(tool.snapshot()).isEqualTo(conflict);
        jdbc.update("DELETE FROM chat_session WHERE id=2200000000000001");
        execute("db/devdata/R__030_chat_demo.sql");
        jdbc.update("UPDATE chat_message SET id=999 WHERE id=2300000000000001");
        var stolenKey = tool.snapshot();
        assertThatThrownBy(() -> execute("db/devdata/R__030_chat_demo.sql")).hasStackTraceContaining("dev_seed_chat_message_identity_conflict");
        assertThat(tool.snapshot()).isEqualTo(stolenKey);
    }

    @Test void historicalV4WithOnlyOldNamesUpgradesTheExplicitV5V6V7V8AndFiveSeeds() throws Exception {
        oldHistory(true);
        assertThat(tool.inspect().pendingVersions()).containsExactly("5", "6", "7", "8");
        var existing = tool.snapshot();
        tool.execute(tool.inspect());
        tool.assertPreserved(existing);
        assertThat(jdbc.queryForObject("SELECT owner_account_id FROM chat_session WHERE id=2200000000000001",Long.class)).isEqualTo(2100000000000201L);
        assertThat(tool.inspect().complete()).isTrue();
    }

    @Test void tenantAndAccountExplicitReexecutionPreservesAllManualChanges() throws Exception {
        tool.flyway().migrate();
        jdbc.update("UPDATE merchant SET merchant_name='operator name',status='DISABLED',timezone='UTC',currency_code='USD',version=4 WHERE id=1000000000000001");
        jdbc.update("UPDATE merchant_staff_account SET password_hash='preserved-digest',status='DISABLED',display_name='operator staff',version=3 WHERE id=2100000000000101");
        jdbc.update("UPDATE consumer_account SET status='DISABLED',display_name='operator consumer' WHERE id=2100000000000201");
        // R__040 demo orders reference the demo consumers; the FK correctly blocks removing a
        // referenced account, so clear its orders before exercising the account seed re-run.
        jdbc.update("DELETE FROM trade_order_item WHERE order_id IN (SELECT id FROM trade_order WHERE customer_id=2100000000000202)");
        jdbc.update("DELETE FROM trade_order WHERE customer_id=2100000000000202");
        var before = tool.snapshot();
        execute("db/seed/nonprod/R__010_local_test_tenants.sql");
        execute("db/devdata/R__020_auth_demo_accounts.sql");
        tool.assertPreserved(before);
        // Deletion does not trigger repeatables on ordinary startup.
        jdbc.update("DELETE FROM consumer_account WHERE id=2100000000000202");
        assertThat(tool.flyway().migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE id=2100000000000202", Integer.class)).isZero();
        execute("db/devdata/R__020_auth_demo_accounts.sql");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE id=2100000000000202", Integer.class)).isOne();
    }

    @Test void tenantIdAndBusinessCodeConflictsFailBeforeWrites() throws Exception {
        structure();
        jdbc.update("INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(1000000000000001,'OTHER','operator')");
        var before = tool.snapshot();
        assertThatThrownBy(() -> execute("db/seed/nonprod/R__010_local_test_tenants.sql"))
                .hasStackTraceContaining("dev_seed_tenant_identity_conflict");
        tool.assertPreserved(before);
        jdbc.update("DELETE FROM merchant WHERE id=1000000000000001");
        jdbc.update("INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(999,'M_3C_DEMO','operator')");
        assertThatThrownBy(() -> execute("db/seed/nonprod/R__010_local_test_tenants.sql"))
                .hasStackTraceContaining("dev_seed_tenant_identity_conflict");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant", Integer.class)).isOne();
    }

    @Test void missingDependenciesFailExplicitlyAndFlywayDoesNotRecordSuccess() {
        structure();
        var accountsOnly = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration", "classpath:db/devdata").load();
        assertThatThrownBy(accountsOnly::migrate).hasStackTraceContaining("dev_seed_missing_M_3C_DEMO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE script='R__020_auth_demo_accounts.sql' AND success=1", Integer.class)).isZero();
    }

    @Test void accountIdsUsernamesPhonesAndOwnershipCannotBeSilentlySkipped() throws Exception {
        structure(); execute("db/seed/nonprod/R__010_local_test_tenants.sql");
        jdbc.update("INSERT INTO merchant_staff_account(id,merchant_id,username,phone,password_hash) VALUES(999,1000000000000001,'demo_staff','+8613900000001','preserve')");
        assertThatThrownBy(() -> execute("db/devdata/R__020_auth_demo_accounts.sql"))
                .hasStackTraceContaining("dev_seed_staff_identity_conflict");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account", Integer.class)).isOne();
        jdbc.update("DELETE FROM merchant_staff_account WHERE id=999");
        jdbc.update("INSERT INTO merchant_staff_account(id,merchant_id,username,phone,password_hash) VALUES(2100000000000101,1000000000000002,'other_staff','+8613900000001','preserve')");
        assertThatThrownBy(() -> execute("db/devdata/R__020_auth_demo_accounts.sql"))
                .hasStackTraceContaining("dev_seed_staff_identity_conflict");
        jdbc.update("DELETE FROM merchant_staff_account WHERE id=2100000000000101");
        jdbc.update("INSERT INTO consumer_account(id,merchant_id,phone) VALUES(999,1000000000000001,'+8613800000401')");
        assertThatThrownBy(() -> execute("db/devdata/R__020_auth_demo_accounts.sql"))
                .hasStackTraceContaining("dev_seed_consumer_identity_conflict");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account", Integer.class)).isZero();
    }

    @Test void secondMissingTenantAndStaffPhoneConflictAreExplicit() throws Exception {
        structure();
        jdbc.update("INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(1000000000000001,'M_3C_DEMO','operator')");
        assertThatThrownBy(() -> execute("db/devdata/R__020_auth_demo_accounts.sql"))
                .hasStackTraceContaining("dev_seed_missing_M_ISOLATION_TEST");
        execute("db/seed/nonprod/R__010_local_test_tenants.sql");
        jdbc.update("INSERT INTO merchant_staff_account(id,merchant_id,username,phone,password_hash) VALUES(999,1000000000000001,'other_staff','+8613800000301','preserve')");
        assertThatThrownBy(() -> execute("db/devdata/R__020_auth_demo_accounts.sql"))
                .hasStackTraceContaining("dev_seed_staff_identity_conflict");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account", Integer.class)).isZero();
    }

    @Test void flywayDependencyFailurePreventsSpringContextFromStarting() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + url, "spring.datasource.username=root",
                        "spring.datasource.password=" + MYSQL.getPassword(),
                        "spring.flyway.locations=classpath:db/migration,classpath:db/devdata")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("dev_seed_missing_M_3C_DEMO");
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account", Integer.class)).isZero();
    }

    @Test void historicalTenantsOnlyUpgradeRetainsHistoryAndRows() throws Exception { upgradeHistorical(false); }
    @Test void historicalAccountsUpgradeRetainsPasswordsDisabledStatesAndHistory() throws Exception { upgradeHistorical(true); }

    private void upgradeHistorical(boolean accounts) throws Exception {
        oldHistory(accounts);
        jdbc.update("UPDATE merchant SET merchant_name='operator tenant',status='DISABLED' WHERE id=1000000000000001");
        jdbc.update("INSERT INTO category(id,type,name,sort,status) VALUES(987654,1,'legacy preserved',1,1)");
        if (accounts) jdbc.update("UPDATE merchant_staff_account SET password_hash='preserved-secret-digest',status='DISABLED',display_name='operator' WHERE id=2100000000000101");
        var before = tool.snapshot();
        var oldHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThatThrownBy(tool.flyway()::migrate).hasMessageContaining("Validate failed");
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(oldHistory);
        var plan = tool.inspect();
        assertThat(plan.retire()).hasSize(accounts ? 2 : 1);
        tool.execute(plan);
        tool.assertPreserved(before);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).containsAll(oldHistory);
        assertThat(jdbc.queryForList("SELECT script FROM flyway_schema_history WHERE type='DELETE'", String.class))
                .containsExactlyInAnyOrderElementsOf(plan.retire());
        assertThat(tool.inspect().complete()).isTrue();
        var after = tool.history();
        tool.execute(tool.inspect());
        assertThat(tool.history()).isEqualTo(after);
    }

    @Test void invalidTargetsAndOtherLiveConnectionsAreRejected() throws Exception {
        assertThatThrownBy(() -> new DevSeedUpgrade(url, "root", "do-not-print", "gba")).hasMessageContaining("local development").hasMessageNotContaining("do-not-print");
        assertThatThrownBy(() -> new DevSeedUpgrade(url.replace("127.0.0.1", "production.example"), "root", "do-not-print", database)).hasMessageNotContaining("do-not-print");
        try (var c = DriverManager.getConnection(url, "root", MYSQL.getPassword())) {
            assertThatThrownBy(tool::assertNoOtherConnections).hasMessageContaining("Stop all applications");
        }
        tool.assertNoOtherConnections();
    }

    @Test void unexpectedMissingFailedAndVersionChecksumDifferencesStopBeforeRepair() throws Exception {
        oldHistory(false);
        var before = tool.history();
        Path migrations = files.resolve("tampered"); Files.createDirectories(migrations);
        for (String name : List.of("V1__legacy_schema_baseline.sql", "V2__merchant_tenant_root.sql", "V3__merchant_currency_compatibility.sql", "V4__auth_accounts.sql", "V5__agent_conversation.sql", "V6__shipment_tables.sql", "V7__order_tables.sql", "V8__inventory_tables.sql")) {
            Files.copy(Path.of("src/main/resources/db/migration", name), migrations.resolve(name));
        }
        // A real failed migration is refused; no history edits are used to manufacture the condition.
        // V99 stays clear of every real migration number applied during oldHistory's full migrate.
        Files.writeString(migrations.resolve("V99__unexpected.sql"), "INSERT INTO table_that_does_not_exist VALUES (1);");
        var unexpected = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("filesystem:" + migrations, "filesystem:" + files.resolve("old")).load();
        assertThatThrownBy(unexpected::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        String failedHistory = tool.history();
        assertThatThrownBy(tool::inspect).isInstanceOf(IllegalStateException.class);
        assertThat(tool.history()).isEqualTo(failedHistory).isNotEqualTo(before);
    }

    @Test void concurrentIndependentFlywayInstancesSerializeOnAnEmptyDatabase() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> migrate = () -> { start.await(); return tool.flyway().migrate().migrationsExecuted; };
            var a = executor.submit(migrate); var b = executor.submit(migrate); start.countDown();
            assertThat(a.get(90, TimeUnit.SECONDS) + b.get(90, TimeUnit.SECONDS)).isEqualTo(13);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account", Integer.class)).isEqualTo(3);
        tool.flyway().validate();
    }

    @Test void unknownHistoricalRepeatableIsRejectedWithoutRepair() throws Exception {
        oldHistory(false);
        Files.writeString(files.resolve("old/R__unexpected_seed.sql"), "SELECT 1;");
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration", "filesystem:" + files.resolve("old")).load().migrate();
        var history = tool.history();
        assertThatThrownBy(tool::inspect).hasMessageContaining("Unknown migration");
        assertThat(tool.history()).isEqualTo(history);
    }

    @Test void versionChecksumMismatchCannotBeAlignedByRenameTool() throws Exception {
        Path altered = files.resolve("altered"); Files.createDirectories(altered);
        try (var scripts = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path script : scripts.toList()) Files.copy(script, altered.resolve(script.getFileName()));
        }
        Files.writeString(altered.resolve("V4__auth_accounts.sql"), "\n-- intentional test checksum mismatch\n", StandardOpenOption.APPEND);
        Path old = files.resolve("old"); Files.createDirectories(old);
        Files.copy(Path.of("src/test/resources/db/upgrade-history/tenants-before-numbering.sql"), old.resolve("R__local_test_tenants.sql"));
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("filesystem:" + altered, "filesystem:" + old).load().migrate();
        var history = tool.history();
        assertThatThrownBy(tool::inspect).hasMessageContaining("Validation differs");
        assertThat(tool.history()).isEqualTo(history);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void cliRequiresBackupRestoreEvidenceAndReportsNoSecrets(boolean startAtV3) throws Exception {
        if (startAtV3) oldV3History("3"); else oldHistory(true);
        jdbc.update("UPDATE merchant SET merchant_name='operator preserved',status='DISABLED' WHERE id=1000000000000002");
        var existing = tool.snapshot();
        var historicalRows = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(tool.inspect().pendingVersions()).isEqualTo(startAtV3 ? List.of("4","5","6","7","8") : List.of("5","6","7","8"));
        Path report = files.resolve("report");
        assertThat(cli("check", report, url, database, false, null)).isZero();
        String before = tool.history();
        assertThat(cli("execute", report, url, database, false, null)).isEqualTo(1);
        assertThat(tool.history()).isEqualTo(before);
        Path backup = files.resolve("before.sql");
        String containerBackup = "/tmp/seed-" + UUID.randomUUID() + ".sql";
        var dump = MYSQL.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysqldump -uroot --single-transaction --routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF \"$1\" --result-file=\"$2\"", "sh", database, containerBackup);
        assertThat(dump.getExitCode()).isZero(); MYSQL.copyFileFromContainer(containerBackup, backup.toString());
        assertThat(cli("execute", report, url, database, true, backup)).isEqualTo(1);
        assertThat(tool.history()).isEqualTo(before);
        String restored = "gba_seed_it_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE `" + restored + "`");
        var restore = MYSQL.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot \"$1\" < \"$2\"", "sh", restored, containerBackup);
        assertThat(restore.getExitCode()).isZero();
        String restoreUrl = url.replace("/" + database + "?", "/" + restored + "?");
        assertThat(cli("verify-restore", report, restoreUrl, restored, false, backup)).isZero();
        assertThat(cli("execute", report, url, database, true, backup)).isZero();
        assertThat(tool.inspect().complete()).isTrue();
        tool.assertPreserved(existing);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).containsAll(historicalRows);
        if (startAtV3) assertThat(jdbc.queryForList("SELECT script FROM flyway_schema_history WHERE type='SQL' ORDER BY installed_rank", String.class))
                .endsWith("V4__auth_accounts.sql", "V5__agent_conversation.sql", "V6__shipment_tables.sql", "V7__order_tables.sql",
                        "V8__inventory_tables.sql", "R__010_local_test_tenants.sql", "R__020_auth_demo_accounts.sql", "R__030_chat_demo.sql",
                        "R__040_shipment_demo_data.sql", "R__050_order_demo_orders.sql");
        assertThat(Files.readString(report.resolve("result.json"))).contains("\"preserved\" : true");
        assertThat(cli("check", files.resolve("reentry"), url, database, false, null)).isZero();
        assertThat(Files.readString(files.resolve("reentry/review.json"))).contains("\"complete\" : true");
    }

    @Test void pendingVersionOutsideApprovedV4V5V6V7SuffixIsRejectedWithoutRepair() throws Exception {
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").target("2").load().migrate();
        String before = tool.history();
        assertThatThrownBy(tool::inspect).hasMessageContaining("Unexpected versioned migration state");
        assertThat(tool.history()).isEqualTo(before);
    }

    @Test void oldAuthSeedWithUnrecordedV4IsNotAcceptedAsTheV3Branch() throws Exception {
        oldV3History("3");
        execute("db/migration/V4__auth_accounts.sql");
        Files.copy(Path.of("src/test/resources/db/upgrade-history/accounts-before-numbering.sql"), files.resolve("old/R__auth_demo_accounts.sql"));
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration", "filesystem:" + files.resolve("old")).target("3").load().migrate();
        String before = tool.history();
        assertThatThrownBy(tool::inspect).hasMessageContaining("Historical auth seed without successful V4");
        assertThat(tool.history()).isEqualTo(before);
    }

    private void oldV3History(String target) throws Exception {
        Path old = files.resolve("old"); Files.createDirectories(old);
        Files.copy(Path.of("src/test/resources/db/upgrade-history/tenants-before-numbering.sql"), old.resolve("R__local_test_tenants.sql"));
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration", "filesystem:" + old).target(target).load().migrate();
    }

    private int cli(String operation, Path report, String targetUrl, String targetDatabase, boolean confirmed, Path backup) throws Exception {
        Path log = files.resolve("cli-" + UUID.randomUUID() + ".log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
                "-DLOG_DIR=target/test-logs", "-cp", System.getProperty("java.class.path"),
                DevSeedUpgrade.class.getName(), operation, report.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
        process.environment().put("DEV_SEED_JDBC_URL", targetUrl);
        process.environment().put("DEV_SEED_DB_USER", "root");
        process.environment().put("DEV_SEED_DB_PASSWORD", MYSQL.getPassword());
        process.environment().put("DEV_SEED_EXPECTED_DATABASE", targetDatabase);
        process.environment().put("DEV_SEED_MAINTENANCE_CONFIRMED", Boolean.toString(confirmed));
        if (backup != null) process.environment().put("DEV_SEED_BACKUP_FILE", backup.toString());
        var running = process.start();
        assertThat(running.waitFor(60, TimeUnit.SECONDS)).isTrue();
        String output = Files.readString(log);
        assertThat(output).doesNotContain("preserved-secret-digest", "$2b$12$", "DevOnly-Auth-2026!", "DEV_SEED_DB_PASSWORD=");
        if (running.exitValue() != 0) assertThat(output).contains("STOP:");
        return running.exitValue();
    }

    void oldHistory(boolean accounts) throws Exception {
        Path old = files.resolve("old"); Files.createDirectories(old);
        Files.copy(Path.of("src/test/resources/db/upgrade-history/tenants-before-numbering.sql"), old.resolve("R__local_test_tenants.sql"));
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration", "filesystem:" + old).target("4").load().migrate();
        if (accounts) {
            Files.copy(Path.of("src/test/resources/db/upgrade-history/accounts-before-numbering.sql"), old.resolve("R__auth_demo_accounts.sql"));
            Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                    .locations("classpath:db/migration", "filesystem:" + old).target("4").load().migrate();
        }
    }

    void structure() { Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).locations("classpath:db/migration").load().migrate(); }
    void execute(String resource) throws Exception {
        try (var c = DriverManager.getConnection(url, "root", MYSQL.getPassword())) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource(resource));
        }
    }
}
