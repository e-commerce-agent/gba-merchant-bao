package com.gba.merchantbao.maintenance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Explicit maintenance entry point; deliberately not a Spring bean or startup hook. */
public final class DevSeedUpgrade {
    public static final Set<String> OLD = Set.of("R__local_test_tenants.sql", "R__auth_demo_accounts.sql");
    public static final Set<String> NEW = Set.of("R__010_local_test_tenants.sql", "R__020_auth_demo_accounts.sql",
            "R__030_chat_demo.sql", "R__040_shipment_demo_data.sql", "R__050_order_demo_orders.sql");
    public static final String[] LOCATIONS = {"classpath:db/migration", "classpath:db/seed/common",
            "classpath:db/seed/nonprod", "classpath:db/devdata"};
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String url;
    private final String user;
    private final String password;
    private final String database;

    public DevSeedUpgrade(String url, String user, String password, String expectedDatabase) {
        URI target;
        try { target = URI.create(url.substring("jdbc:".length())); }
        catch (RuntimeException e) { throw rejected("Invalid local development target"); }
        if (!url.startsWith("jdbc:mysql://") || !Set.of("127.0.0.1", "localhost").contains(target.getHost())
                || target.getUserInfo() != null || target.getFragment() != null
                || !("gba_dev".equals(expectedDatabase) || expectedDatabase.matches("gba_seed_it_[a-f0-9]+"))
                || !target.getPath().equals("/" + expectedDatabase)
                || (target.getQuery() != null && !target.getQuery().matches("(?:allowPublicKeyRetrieval=true|useSSL=false|serverTimezone=UTC)(?:&(?:allowPublicKeyRetrieval=true|useSSL=false|serverTimezone=UTC))*"))) {
            throw rejected("Only the explicitly named local development database is allowed");
        }
        this.url = url; this.user = user; this.password = password; this.database = expectedDatabase;
    }

    public Flyway flyway() {
        return Flyway.configure().dataSource(url, user, password).locations(LOCATIONS)
                .baselineOnMigrate(false).validateOnMigrate(true).cleanDisabled(true).outOfOrder(false).load();
    }

    public record Plan(String database, List<String> retire, List<String> pending, List<String> pendingVersions, boolean complete) { }

    public Plan inspect() {
        for (String old : OLD) {
            for (String directory : List.of("db/seed/nonprod/", "db/devdata/")) {
                if (DevSeedUpgrade.class.getClassLoader().getResource(directory + old) != null)
                    throw rejected("Stale runtime migration resources; remove old build output and rebuild");
            }
        }
        var flyway = flyway();
        Set<String> versions = new TreeSet<>();
        Set<String> pendingVersions = new TreeSet<>();
        Set<String> retire = new TreeSet<>();
        Set<String> pending = new TreeSet<>();
        Set<String> successful = new HashSet<>();
        boolean knownOldTenant = false;
        boolean knownOldAccounts = false;
        for (var info : flyway.info().all()) {
            var state = info.getState();
            String script = info.getScript();
            if (info.getVersion() != null) {
                String version = info.getVersion().getVersion();
                boolean knownPending = (state == MigrationState.PENDING
                        && ("4".equals(version) && "V4__auth_accounts.sql".equals(script)
                        || "5".equals(version) && "V5__agent_conversation.sql".equals(script)
                        || "6".equals(version) && "V6__shipment_tables.sql".equals(script)
                        || "7".equals(version) && "V7__order_tables.sql".equals(script)
                        || "8".equals(version) && "V8__inventory_tables.sql".equals(script)));
                if (knownPending) pendingVersions.add(version);
                else if (state != MigrationState.SUCCESS || !versions.add(version))
                    throw rejected("Unexpected versioned migration state");
            } else if (OLD.contains(script)) {
                if (script.equals("R__local_test_tenants.sql")) knownOldTenant = true;
                if (script.equals("R__auth_demo_accounts.sql")) knownOldAccounts = true;
                if (state == MigrationState.MISSING_SUCCESS) retire.add(script);
                else if (state != MigrationState.DELETED && state != MigrationState.SUPERSEDED
                        && !(state == MigrationState.SUCCESS && "DELETE".equals(info.getType().toString())))
                    throw rejected("Unexpected historical repeatable state: " + state + "/" + info.getType());
            } else if (NEW.contains(script)) {
                if (state == MigrationState.PENDING) pending.add(script);
                else if (state == MigrationState.SUCCESS) successful.add(script);
                else throw rejected("New repeatable is not Pending or Success");
            } else throw rejected("Unknown migration outside the rename allowlist");
        }
        if (!(versions.equals(Set.of("1", "2", "3", "4", "5", "6", "7", "8")) && pendingVersions.isEmpty())
                && !(versions.equals(Set.of("1", "2", "3", "4", "5", "6", "7")) && pendingVersions.equals(Set.of("8")))
                && !(versions.equals(Set.of("1", "2", "3", "4", "5", "6")) && pendingVersions.equals(Set.of("7", "8")))
                && !(versions.equals(Set.of("1", "2", "3", "4", "5")) && pendingVersions.equals(Set.of("6", "7", "8")))
                && !(versions.equals(Set.of("1", "2", "3", "4")) && pendingVersions.equals(Set.of("5", "6", "7", "8")))
                && !(versions.equals(Set.of("1", "2", "3")) && pendingVersions.equals(Set.of("4", "5", "6", "7", "8"))))
            throw rejected("Expected successful V1 through V8, or a supported historical V3/V4/V5/V6/V7 database with only the next pending");
        if (pendingVersions.contains("4") && knownOldAccounts)
            throw rejected("Historical auth seed without successful V4 is not a supported V3 database");
        var validation = flyway.validateWithResult();
        if (!validation.validationSuccessful && (validation.invalidMigrations == null || validation.invalidMigrations.isEmpty()))
            throw rejected("Migration validation failed without an approved rename difference");
        if (validation.invalidMigrations != null) {
            for (var invalid : validation.invalidMigrations) {
                String script = "R__" + invalid.description.replace(' ', '_') + ".sql";
                boolean approvedMissing = "APPLIED_REPEATABLE_MIGRATION_NOT_RESOLVED".equals(invalid.errorDetails.errorCode.toString())
                        && retire.contains(script);
                boolean approvedPending = "RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED".equals(invalid.errorDetails.errorCode.toString())
                        && pending.contains(script);
                boolean approvedPendingVersion = "RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(invalid.errorDetails.errorCode.toString())
                        && (("4".equals(invalid.version) && "auth accounts".equals(invalid.description))
                        || ("5".equals(invalid.version) && "agent conversation".equals(invalid.description))
                        || ("6".equals(invalid.version) && "shipment tables".equals(invalid.description))
                        || ("7".equals(invalid.version) && "order tables".equals(invalid.description))
                        || ("8".equals(invalid.version) && "inventory tables".equals(invalid.description)))
                        && pendingVersions.contains(invalid.version);
                if (!approvedMissing && !approvedPending && !approvedPendingVersion)
                    throw rejected("Validation differs from the two approved repeatable renames");
            }
        }
        boolean complete = retire.isEmpty() && pending.isEmpty() && pendingVersions.isEmpty() && successful.equals(NEW);
        if (!complete && (!knownOldTenant || !pending.equals(NEW) || !successful.isEmpty()))
            throw rejected("Not a supported old-name database or completed transition");
        return new Plan(database, List.copyOf(retire), List.copyOf(pending), List.copyOf(pendingVersions), complete);
    }

    /** Hash every existing row, including legacy rows; reports contain no column values. */
    public Map<String, Map<String, String>> snapshot() throws Exception {
        Map<String, Map<String, String>> result = new TreeMap<>();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            var tables = connection.getMetaData().getTables(database, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if (table.equals("flyway_schema_history")) continue;
                if (!table.matches("[a-zA-Z0-9_]+")) throw rejected("Unsupported table identifier");
                List<String> primary = new ArrayList<>();
                try (var keys = connection.getMetaData().getPrimaryKeys(database, null, table)) {
                    var ordered = new TreeMap<Short, String>();
                    while (keys.next()) ordered.put(keys.getShort("KEY_SEQ"), keys.getString("COLUMN_NAME"));
                    primary.addAll(ordered.values());
                }
                if (primary.isEmpty()) throw rejected("Cannot verify preservation without a primary key");
                var rows = new TreeMap<String, String>();
                try (var statement = connection.createStatement(); var data = statement.executeQuery("SELECT * FROM `" + table + "`")) {
                    while (data.next()) {
                        List<String> values = new ArrayList<>();
                        List<String> key = new ArrayList<>();
                        for (String column : primary) key.add(data.getString(column));
                        for (int i = 1; i <= data.getMetaData().getColumnCount(); i++) values.add(data.getString(i));
                        rows.put(digest(JSON.writeValueAsBytes(key)), digest(JSON.writeValueAsBytes(values)));
                    }
                }
                result.put(table, rows);
            }
        }
        return result;
    }

    public void assertPreserved(Map<String, Map<String, String>> before) throws Exception {
        var after = snapshot();
        for (var table : before.entrySet()) {
            var rows = after.get(table.getKey());
            if (rows == null || !rows.entrySet().containsAll(table.getValue().entrySet()))
                throw rejected("An existing business row changed; stop maintenance and retain evidence");
        }
    }

    public void execute(Plan reviewed) throws Exception {
        if (!inspect().equals(reviewed)) throw rejected("History changed since review");
        var before = snapshot();
        if (reviewed.complete()) { flyway().validate(); return; }
        var flyway = flyway();
        if (!reviewed.retire().isEmpty()) {
            var repair = flyway.repair();
            Set<String> deleted = new TreeSet<>();
            // Missing resources have no resolved filepath in RepairOutput. Their
            // repeatable description is the identity used by Flyway itself.
            for (var item : repair.migrationsDeleted) deleted.add("R__" + item.description.replace(' ', '_') + ".sql");
            if (!deleted.equals(new TreeSet<>(reviewed.retire()))
                    || !repair.migrationsAligned.isEmpty() || !repair.migrationsRemoved.isEmpty())
                throw rejected("Repair changed more than the approved missing repeatables");
        }
        var repaired = inspect();
        if (!repaired.retire().isEmpty() || !new HashSet<>(repaired.pending()).equals(NEW)
                || !repaired.pendingVersions().equals(reviewed.pendingVersions()))
            throw rejected("Unexpected post-repair history");
        if (flyway.migrate().migrationsExecuted != repaired.pending().size() + reviewed.pendingVersions().size())
            throw rejected("Unexpected migration count");
        flyway.validate();
        if (!inspect().complete()) throw rejected("Transition did not finish");
        assertPreserved(before);
    }

    public void assertNoOtherConnections() throws SQLException {
        try (var c = DriverManager.getConnection(url, user, password); var statement = c.createStatement();
             var data = statement.executeQuery("SELECT COUNT(*) FROM information_schema.processlist WHERE DB=DATABASE() AND ID<>CONNECTION_ID()")) {
            data.next();
            if (data.getInt(1) != 0) throw rejected("Stop all applications and database clients connected to the target");
        }
    }

    public static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static IllegalStateException rejected(String reason) { return new IllegalStateException(reason); }

    public static void main(String[] args) {
        try {
            if (args.length != 2 || !Set.of("check", "execute", "verify-restore").contains(args[0])) throw rejected("Use check|execute|verify-restore and a report directory");
            var tool = new DevSeedUpgrade(env("DEV_SEED_JDBC_URL"), env("DEV_SEED_DB_USER"), env("DEV_SEED_DB_PASSWORD"), env("DEV_SEED_EXPECTED_DATABASE"));
            tool.assertNoOtherConnections();
            Path report = Path.of(args[1]).toAbsolutePath(); Files.createDirectories(report);
            Path planFile = report.resolve("review.json");
            if (args[0].equals("verify-restore")) {
                var before = JSON.readValue(report.resolve("rows-before.json").toFile(), new TypeReference<Map<String, Map<String, String>>>() { });
                if (!tool.snapshot().equals(before) || !tool.history().equals(Files.readString(report.resolve("history-before.json"))))
                    throw rejected("Restored database does not match original business rows and history");
                String backupHash = digest(Files.readAllBytes(Path.of(env("DEV_SEED_BACKUP_FILE"))));
                JSON.writerWithDefaultPrettyPrinter().writeValue(report.resolve("restore-evidence.json").toFile(), Map.of(
                        "backupSha256", backupHash, "restoredDatabase", tool.database, "verifiedAt", Instant.now().toString()));
                System.out.println("RESTORE OK: all row hashes and migration history match");
            } else if (args[0].equals("check")) {
                var plan = tool.inspect();
                JSON.writerWithDefaultPrettyPrinter().writeValue(planFile.toFile(), plan);
                JSON.writeValue(report.resolve("rows-before.json").toFile(), tool.snapshot());
                Files.writeString(report.resolve("history-before.json"), tool.history());
                System.out.println("CHECK OK: " + (plan.complete() ? "already complete" : "review report before explicit execute"));
            } else {
                var plan = JSON.readValue(planFile.toFile(), Plan.class);
                var before = JSON.readValue(report.resolve("rows-before.json").toFile(), new TypeReference<Map<String, Map<String, String>>>() { });
                if (!"true".equals(System.getenv("DEV_SEED_MAINTENANCE_CONFIRMED"))) throw rejected("Maintenance confirmation missing");
                Path backup = Path.of(env("DEV_SEED_BACKUP_FILE"));
                String backupHash = digest(Files.readAllBytes(backup));
                var evidence = JSON.readTree(report.resolve("restore-evidence.json").toFile());
                if (Files.size(backup) == 0 || !backupHash.equals(evidence.path("backupSha256").asText())
                        || !evidence.path("restoredDatabase").asText().matches("gba_seed_it_[a-f0-9]+"))
                    throw rejected("Backup restore evidence does not match");
                if (!tool.snapshot().equals(before)) throw rejected("Business rows changed since the reviewed backup");
                tool.execute(plan);
                Files.writeString(report.resolve("history-after.json"), tool.history());
                JSON.writerWithDefaultPrettyPrinter().writeValue(report.resolve("result.json").toFile(), Map.of(
                        "database", tool.database, "completedAt", Instant.now().toString(), "backupSha256", backupHash,
                        "preserved", true, "complete", tool.inspect().complete()));
                System.out.println("UPGRADE OK: existing rows preserved, history retained, validation passed");
            }
        } catch (Exception failure) {
            // No JDBC URL, password, SQL values, or exception chain in maintenance output.
            System.err.println("STOP: " + (failure instanceof IllegalStateException ? failure.getMessage() : failure.getClass().getSimpleName())
                    + "; retain backup and reports; no automatic recovery");
            System.exit(1);
        }
    }

    public String history() throws Exception {
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement();
             var rows = s.executeQuery("SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")) {
            var result = new ArrayList<Map<String, Object>>();
            while (rows.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.put(rows.getMetaData().getColumnLabel(i), rows.getObject(i));
                result.add(row);
            }
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw rejected("Required environment variable: " + name);
        return value;
    }
}
