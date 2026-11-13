package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.config.AuthJwtConfiguration;
import com.gba.merchantbao.auth.config.AuthJwtKeyMaterial;
import com.gba.merchantbao.auth.config.AuthJwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AuthJwtConfigurationTest {
    static final String STAFF = key('A', 32);
    static final String CONSUMER = key('B', 32);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthJwtConfiguration.class)
            .withPropertyValues("merchant.auth.jwt.issuer=merchantbao", "merchant.auth.jwt.audience=api-v1");

    static String key(char value, int length) {
        return Base64.getEncoder().encodeToString(String.valueOf(value).repeat(length).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void independentStrongKeysStartAndPropertiesDoNotExposeSecrets() {
        configured(STAFF, CONSUMER).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AuthJwtKeyMaterial.class);
            assertThat(context.getBean(AuthJwtProperties.class).toString()).doesNotContain(STAFF, CONSUMER);
        });
    }

    @Test
    void missingAndInvalidKeysFailStartupWithoutIncludingTheirValues() {
        runner.run(context -> assertThat(context).hasFailed());
        for (String invalid : new String[]{"", "REPLACE_WITH_STRONG_BASE64_KEY", "CHANGE_ME", "invalid!base64", key('S', 31)}) {
            configured(invalid, CONSUMER).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("merchant.auth.jwt.merchant-staff-secret-base64");
                if (!invalid.isEmpty()) assertThat(stack(context.getStartupFailure())).doesNotContain(invalid);
            });
            configured(STAFF, invalid).run(context -> {
                assertThat(context).hasFailed();
                if (!invalid.isEmpty()) assertThat(stack(context.getStartupFailure())).doesNotContain(invalid);
            });
        }
        runner.withPropertyValues("merchant.auth.jwt.merchant-staff-secret-base64=" + STAFF)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void equalKeysAndProtocolOverridesFailStartupWithoutExposingKeys() {
        configured(STAFF, STAFF).run(context -> {
            assertThat(context).hasFailed();
            assertThat(stack(context.getStartupFailure())).contains("must be different").doesNotContain(STAFF);
        });
        for (String property : new String[]{"issuer", "audience"}) {
            configured(STAFF, CONSUMER).withPropertyValues("merchant.auth.jwt." + property + "=wrong")
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    private ApplicationContextRunner configured(String staff, String consumer) {
        return runner.withPropertyValues("merchant.auth.jwt.merchant-staff-secret-base64=" + staff,
                "merchant.auth.jwt.consumer-secret-base64=" + consumer);
    }

    private static String stack(Throwable error) {
        var output = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(output));
        return output.toString();
    }
}
