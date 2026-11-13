package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.service.SmsSender;
import com.gba.merchantbao.auth.config.AuthSmsConfiguration;
import com.gba.merchantbao.auth.config.AuthSmsConfiguration.SmsPolicy;
import com.gba.merchantbao.auth.exception.AuthError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AuthSmsConfigurationTest {
    private ApplicationContextRunner context(String profile, String provider, boolean debug) {
        return new ApplicationContextRunner().withUserConfiguration(AuthSmsConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + profile, "merchant.auth.sms.provider=" + provider,
                        "merchant.auth.sms.expose-debug-code=" + debug);
    }
    @Test void mockIsNonproductionAndEchoRequiresExplicitDevFlag() {
        context("dev", "mock", true).run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(SmsPolicy.class).exposeDebugCode()).isTrue();
            c.getBean(SmsSender.class).sendLoginCode("+8613800138000", "654321");
        });
        context("dev", "mock", false).run(c -> assertThat(c.getBean(SmsPolicy.class).exposeDebugCode()).isFalse());
        context("test", "mock", true).run(c -> assertThat(c.getBean(SmsPolicy.class).exposeDebugCode()).isFalse());
        context("prod", "mock", false).run(c -> assertThat(c).hasFailed());
        context("prod", "disabled", true).run(c -> assertThat(c).hasFailed());
        context("dev,prod", "mock", true).run(c -> assertThat(c).hasFailed());
        context("staging", "mock", false).run(c -> assertThat(c).hasFailed());
    }
    @Test void disabledFailsWithoutPretendingToSendAndTestCanReplaceTransport() {
        context("prod", "disabled", false).run(c -> {
            assertThat(c).hasNotFailed();
            assertThatThrownBy(() -> c.getBean(SmsSender.class).sendLoginCode("+8613800138000", "654321"))
                    .isInstanceOfSatisfying(AuthError.class, e -> assertThat(e.status()).isEqualTo(503));
        });
        SmsSender stub = (phone, code) -> { throw new IllegalStateException("transport failed"); };
        context("test", "mock", false).withBean(SmsSender.class, () -> stub)
                .run(c -> assertThat(c.getBean(SmsSender.class)).isSameAs(stub));
    }
}
