package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.test.support.DockerRequired;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Deliberately opts into devdata/mock on disposable infrastructure; normal test profile excludes it. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "merchant.auth.namespace=dev-manual-it","merchant.auth.sms.provider=mock","merchant.auth.sms.expose-debug-code=true",
    "spring.flyway.locations=classpath:db/migration,classpath:db/seed/common,classpath:db/seed/nonprod,classpath:db/devdata"})
@ActiveProfiles({"dev","test"}) @Testcontainers @DirtiesContext
class AuthDevManualIT {
    static {DockerRequired.check();}
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34").withDatabaseName("auth_manual").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
    }
    @LocalServerPort int port;@Autowired TestRestTemplate http;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Autowired org.flywaydb.core.Flyway flyway;
    @Test void executeManualScriptAndExportActualSpringdocThenVerifyDevSeedIsNonDestructive() throws Exception {
        String hash=jdbc.queryForObject("SELECT password_hash FROM merchant_staff_account WHERE id=2100000000000101",String.class);
        assertThat(org.springframework.security.crypto.bcrypt.BCrypt.checkpw("DevOnly-Auth-2026!",hash)).as("dev password digest").isTrue();
        var login=http.postForEntity("/api/v1/auth/staff/login",Map.of("merchantCode","M_3C_DEMO","identifier","demo_staff","password","DevOnly-Auth-2026!"),String.class);
        assertThat(login.getStatusCode().value()).as("dev HTTP login").isEqualTo(200);
        var process=new ProcessBuilder("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",
            Path.of("scripts/test/manual-verification.ps1").toAbsolutePath().toString(),"-BaseUrl","http://127.0.0.1:"+port)
            .redirectErrorStream(true).redirectOutput(Path.of("target/auth-manual-verification.log").toFile()).start();
        assertThat(process.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output=Files.readString(Path.of("target/auth-manual-verification.log"),java.nio.charset.Charset.forName("GB18030"));
        assertThat(process.exitValue()).as(output).isZero();assertThat(output).contains("PASS backend manual verification complete").doesNotContain("DevOnly-Auth-2026!","accessToken","refreshToken","debugCode");
        var response=http.getForEntity("/v3/api-docs/auth-v1",String.class);assertThat(response.getStatusCode().value()).isEqualTo(200);
        var doc=json.readTree(response.getBody());List<String> operations=new ArrayList<>();doc.path("paths").fields().forEachRemaining(p->p.getValue().fields().forEachRemaining(op->operations.add(op.getValue().path("operationId").asText())));
        assertThat(operations).containsExactlyInAnyOrder("sendLoginSms","consumerLogin","staffLogin","refreshAuth","logoutAuth","authMe","createStaffAccount","getStaffAccount","getConsumerAccount");
        assertThat(doc.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(response.getBody()).contains("refreshToken","refreshRequestId","refreshExpiresAt","debugCode","Retry-After","no-store","11502");
        var created=doc.path("paths").path("/api/v1/staff-accounts").path("post").path("responses");
        assertThat(created.has("200")).isFalse();
        String createdRef=created.path("201").path("content").path("application/json").path("schema").path("$ref").asText();
        assertThat(createdRef).isEqualTo("#/components/schemas/ApiResponseAccountView");
        assertThat(doc.path("components").path("schemas").has("ApiResponseAccountView")).isTrue();
        assertThat(doc.path("components").path("schemas").path("StaffLogin").path("properties").has("merchantCode")).isTrue();
        String yaml=http.getForObject("/v3/api-docs.yaml/auth-v1",String.class);assertThat(yaml).startsWith("openapi:");Files.writeString(Path.of("target/auth-v1.openapi.yaml"),yaml);
        jdbc.update("UPDATE merchant_staff_account SET status='DISABLED',password_hash='preserve-existing' WHERE id=2100000000000101");
        flyway.migrate();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM merchant_staff_account WHERE id=2100000000000101",String.class)).isEqualTo("preserve-existing");
        assertThat(jdbc.queryForObject("SELECT status FROM merchant_staff_account WHERE id=2100000000000101",String.class)).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_staff_account WHERE username='demo_staff'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE id BETWEEN 2100000000000201 AND 2100000000000203",Integer.class)).isEqualTo(3);
        assertThat(Files.readString(Path.of("src/test/resources/application-test.yml"))).doesNotContain("classpath:db/devdata");
        assertThat(Files.readString(Path.of("src/main/resources/application-prod.yml"))).doesNotContain("classpath:db/devdata","classpath:db/seed/nonprod");
    }
}
