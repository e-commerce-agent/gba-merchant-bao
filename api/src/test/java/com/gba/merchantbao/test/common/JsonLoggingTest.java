package com.gba.merchantbao.test.common;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.logging.AuthAudit;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.common.privacy.PhoneMasker;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLoggingTest {
    @Test
    void jsonFilesPreserveCorrelationAndKeepAuditSeparate() throws Exception {
        Path directory = Path.of(System.getProperty("LOG_DIR"), "json-probe");
        var system = LoggingSystem.get(getClass().getClassLoader());
        ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        system.beforeInitialize();
        system.initialize(new LoggingInitializationContext(new MockEnvironment()
                .withProperty("logging.file.path", directory.toAbsolutePath().toString())),
                "classpath:logback-spring.xml", null);
        String marker = UUID.randomUUID().toString();
        try {
            MDC.put("requestId", marker);
            MDC.put("module", "foundation");
            MDC.put("errorCode", "10000");
            LoggerFactory.getLogger("com.gba.merchantbao.test.probe")
                    .info("phone={}", PhoneMasker.maskPhone("13800138000"));
            LoggerFactory.getLogger("com.gba.merchantbao.legacy.audit")
                    .info("audit-probe {}", marker);
            com.gba.merchantbao.auth.logging.AuthAudit.record("POST:/api/v1/auth/staff/login",
                    new com.gba.merchantbao.auth.security.Actor(new com.gba.merchantbao.auth.security.AuthPrincipal(101,202,
                            com.gba.merchantbao.auth.enums.AccountRole.MERCHANT_STAFF),"never-log-session"),0,12);
            var mapper = new ObjectMapper();
            String application = Files.readString(directory.resolve("application.json"));
            var event = application.lines().map(line -> {
                try { return mapper.readTree(line); }
                catch (Exception error) { throw new AssertionError("Invalid JSON log", error); }
            }).filter(node -> marker.equals(node.path("requestId").asText())).findFirst().orElseThrow();
            assertThat(event.path("module").asText()).isEqualTo("foundation");
            assertThat(event.path("errorCode").asText()).isEqualTo("10000");
            assertThat(event.path("message").asText()).contains("138****8000").doesNotContain("13800138000");
            assertThat(application).doesNotContain("audit-probe " + marker);
            assertThat(Files.readString(directory.resolve("audit.json"))).contains(marker);
            String audit=Files.readString(directory.resolve("audit.json"));
            var auth=audit.lines().map(line->{try{return mapper.readTree(line);}catch(Exception e){throw new AssertionError(e);}})
                    .filter(n->marker.equals(n.path("requestId").asText())&&"auth".equals(n.path("module").asText())).findFirst().orElseThrow();
            assertThat(auth.path("actor").asText()).isEqualTo("101");
            assertThat(auth.path("merchant").asText()).isEqualTo("202");
            assertThat(auth.path("code").asInt()).isZero();
            assertThat(auth.path("durationMs").asInt()).isEqualTo(12);
            assertThat(audit).doesNotContain("never-log-session","13800138000","password","passwordHash","accessToken","refreshToken","debugCode");
            assertThat(application).doesNotContain("auth_event");
            String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
            assertThat(config).contains("SizeAndTimeBasedRollingPolicy", "%i.json.gz", "100MB",
                    "<maxHistory>7</maxHistory>", "<maxHistory>30</maxHistory>",
                    "<totalSizeCap>2GB</totalSizeCap>", "<totalSizeCap>1GB</totalSizeCap>");
            assertThat(config).doesNotContain("error.json");
        } finally {
            MDC.clear();
            system.cleanUp();
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).reset();
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(new MockEnvironment()
                    .withProperty("logging.file.path", System.getProperty("LOG_DIR"))),
                    "classpath:logback-spring.xml", null);
        }
    }
}
