package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.AfterTransaction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FoundationApplicationIT {
    static {
        DockerRequired.check();
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("merchant_test").withUsername("test").withPassword("test")
            .withReuse(false);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withReuse(false);

    @DynamicPropertySource
    static void containerConnections(DynamicPropertyRegistry registry) {
        registry.add("testcontainers.mysql.url", MYSQL::getJdbcUrl);
        registry.add("testcontainers.mysql.username", MYSQL::getUsername);
        registry.add("testcontainers.mysql.password", MYSQL::getPassword);
        registry.add("testcontainers.redis.host", REDIS::getHost);
        registry.add("testcontainers.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RedisConnectionFactory redis;

    @Test
    void applicationStartsWithFlywayFixturesAndContainerRedis() {
        assertThat(MYSQL.getMappedPort(3306)).isNotIn(3306, 3307);
        assertThat(REDIS.getMappedPort(6379)).isNotIn(6379, 6380);
        for (var container : java.util.List.of(MYSQL, REDIS)) {
            assertThat(container.getContainerInfo().getMounts()).noneSatisfy(mount ->
                    assertThat(mount.getName()).isIn("gba-dev_mysql_data", "gba-dev_redis_data"));
        }
        assertThat(jdbc.queryForList("SELECT merchant_code FROM merchant WHERE merchant_code IN "
                + "('M_3C_DEMO', 'M_ISOLATION_TEST') ORDER BY merchant_code", String.class))
                .containsExactly("M_3C_DEMO", "M_ISOLATION_TEST");
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE type='SQL' AND version IS NOT NULL "
                + "AND success=1 ORDER BY installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(jdbc.queryForList("SELECT script FROM flyway_schema_history WHERE success=0", String.class)).isEmpty();
        assertThat(jdbc.queryForList("SELECT script FROM flyway_schema_history WHERE version IS NULL AND success=1", String.class))
                .contains("R__010_local_test_tenants.sql");
        try (var connection = redis.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    @Transactional
    @Sql("/db/fixture/merchant_probe.sql")
    void fixtureIsLocatedByStableTenantCodeAndRolledBack() {
        assertThat(jdbc.queryForObject("SELECT merchant_name FROM merchant WHERE merchant_code = ?",
                String.class, "M_FIXTURE_RUNTIME" )).isEqualTo("Runtime fixture");
    }

    @AfterTransaction
    void fixtureDidNotLeakOutsideTheTestTransaction() {
        assertThat(jdbc.queryForList("SELECT id FROM merchant WHERE merchant_code = ?",
                Long.class, "M_FIXTURE_RUNTIME")).isEmpty();
    }

    @Test
    void runningServerExposesHealthAndOpenApiYaml() {
        var health = http.getForEntity("/api/v1/health", String.class);
        assertThat(health.getStatusCode().value()).isEqualTo(200);
        assertThat(health.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(health.getBody()).contains("\"status\":\"UP\"", "\"requestId\"");
        var yaml = http.getForEntity("/v3/api-docs.yaml", String.class);
        assertThat(yaml.getStatusCode().value()).isEqualTo(200);
        assertThat(yaml.getBody()).contains("openapi:", "/api/v1/health:", "getFoundationHealth");
    }
}
