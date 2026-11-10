package com.gba.merchantbao.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用真实生产初始化脚本，在可销毁容器演练；不读取实际 .env。 */
@Testcontainers
class ProductionInfrastructureIT {
    static { DockerRequired.check(); }

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("gba").withUsername("test").withPassword("test")
            .withEnv("FLYWAY_PASSWORD", "migration-test")
            .withEnv("APP_DB_PASSWORD", "application-test")
            .withCopyFileToContainer(MountableFile.forHostPath(
                    "docs/prod-profile/opt/gba/docker/mysql/init/01-create-users.sh", 0644),
                    "/docker-entrypoint-initdb.d/01-create-users.sh")
            .withReuse(false);

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withEnv("REDISCLI_AUTH", "redis-test")
            .withCommand("redis-server", "--appendonly", "yes", "--requirepass", "redis-test")
            .withExposedPorts(6379).withReuse(false);

    @Test
    void migrationAccountCanMigrateButApplicationHasOnlyDmlAndBackupCanRestore() throws Exception {
        var flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), "flyway_deploy", "migration-test")
                .locations("classpath:db/migration", "classpath:db/seed/common")
                .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(true)
                .validateOnMigrate(true).outOfOrder(false).load();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(flyway.info().applied()).allSatisfy(info ->
                assertThat(info.getState()).isEqualTo(org.flywaydb.core.api.MigrationState.SUCCESS));
        flyway.validate();
        try (var app = DriverManager.getConnection(mysql.getJdbcUrl(), "app", "application-test");
             var statement = app.createStatement()) {
            statement.executeUpdate("INSERT INTO merchant (id,merchant_code,merchant_name,currency_code) "
                    + "VALUES (991,'M_BACKUP_PROBE','backup probe','CNY')");
            statement.executeUpdate("UPDATE merchant SET version=1 WHERE merchant_code='M_BACKUP_PROBE'");
            assertThatThrownBy(() -> statement.execute("CREATE TABLE forbidden (id BIGINT)"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("SELECT * FROM mysql.user"))
                    .isInstanceOf(java.sql.SQLException.class);
            var grants = statement.executeQuery("SHOW GRANTS");
            StringBuilder permissions = new StringBuilder();
            while (grants.next()) permissions.append(grants.getString(1));
            assertThat(permissions.toString()).contains("SELECT, INSERT, UPDATE, DELETE", "`gba`.*")
                    .doesNotContain("GRANT OPTION", "ALL PRIVILEGES");
        }
        // 容器内 result-file 避免 PowerShell 重定向改变 SQL 编码。
        successful(mysql.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysqldump -uroot "
                + "--single-transaction --routines --events --triggers --no-tablespaces "
                + "--set-gtid-purged=OFF gba --result-file=/tmp/gba-data.sql"));
        Path backup = Path.of("target", "backup-rehearsal", "gba-data.sql");
        Files.createDirectories(backup.getParent());
        mysql.copyFileFromContainer("/tmp/gba-data.sql", backup.toString());
        assertThat(Files.readString(backup)).contains("CREATE TABLE `merchant`", "M_BACKUP_PROBE");
        successful(mysql.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot "
                + "-e 'CREATE DATABASE gba_restore'"));
        successful(mysql.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot "
                + "gba_restore < /tmp/gba-data.sql"));
        var restored = mysql.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -N -uroot "
                + "gba_restore -e \"SELECT merchant_code, version FROM merchant WHERE merchant_code='M_BACKUP_PROBE'\"");
        successful(restored);
        assertThat(restored.getStdout()).contains("M_BACKUP_PROBE\t1");
        // 新增初始化脚本后重启同一个容器，官方 entrypoint 不对已有数据目录重跑 init。
        mysql.copyFileToContainer(org.testcontainers.images.builder.Transferable.of(
                "echo should-not-run > /var/lib/mysql/initialization-reran\n"),
                "/docker-entrypoint-initdb.d/99-restart-probe.sh");
        var docker = org.testcontainers.DockerClientFactory.instance().client();
        docker.restartContainerCmd(mysql.getContainerId()).exec();
        org.rnorth.ducttape.unreliables.Unreliables.retryUntilSuccess(60, java.util.concurrent.TimeUnit.SECONDS,
                () -> {
                    var probe = mysql.execInContainer("sh", "-c",
                            "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -h127.0.0.1 -uroot -e 'SELECT 1'");
                    if (probe.getExitCode() != 0) throw new IllegalStateException(probe.getStderr());
                    return true;
                });
        assertThat(mysql.execInContainer("test", "-e", "/var/lib/mysql/initialization-reran").getExitCode()).isEqualTo(1);
        // 重启后通过容器内 TCP 核验持久数据，不依赖 Docker Desktop 重新发布随机端口的时机。
        var history = mysql.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -N -uroot "
                + "gba -e 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1'");
        successful(history);
        assertThat(history.getStdout().trim()).isEqualTo("8");
    }

    @Test
    void productionRedisRequiresAuthenticationAndSupportsAuthenticatedHealthCheck() throws Exception {
        var ping = redis.execInContainer("redis-cli", "ping");
        successful(ping);
        assertThat(ping.getStdout()).contains("PONG");
        var anonymous = redis.execInContainer("sh", "-c", "unset REDISCLI_AUTH; redis-cli ping");
        assertThat(anonymous.getStdout()).contains("NOAUTH");
    }

    private static void successful(org.testcontainers.containers.Container.ExecResult result) {
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }
}
