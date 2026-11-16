package com.gba.merchantbao.test.agent;

import com.gba.merchantbao.agent.config.AgentConfiguration;
import com.gba.merchantbao.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AgentConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AgentConfiguration.class);
    @Test void disabledNeedsNoModelCredentials() {
        runner.run(c -> { assertThat(c).hasNotFailed(); assertThat(c.getBean(AgentProperties.class).isEnabled()).isFalse(); });
    }
    @Test void enabledRejectsMissingOrInvalidConfigWithoutEchoingSecrets() {
        runner.withPropertyValues("merchant.agent.enabled=true").run(c -> assertThat(c).hasFailed());
        for (String invalid : new String[]{"merchant.agent.base-url=https://host/v1/chat/completions",
                "merchant.agent.model=YOUR_MODEL", "merchant.agent.total-timeout=0s", "merchant.agent.max-input-chars=4001",
                "merchant.agent.api-key=YOUR_API_KEY", "merchant.agent.allowed-origins[0]=*",
                "merchant.agent.api-key-header=bad header", "merchant.agent.api-key-header="}) {
            configured().withPropertyValues(invalid).run(c -> {
                assertThat(c).hasFailed();
                assertThat(c.getStartupFailure()).hasStackTraceContaining("merchant.agent");
                assertThat(c.getStartupFailure()).hasStackTraceContaining("Invalid");
                assertThat(c.getStartupFailure().toString()).doesNotContain("secret-test-value");
            });
        }
    }
    @Test void apiRootAndLimitsAreValidatedOnStartup() {
        configured().run(c -> assertThat(c).hasNotFailed());
        configured().withPropertyValues("merchant.agent.api-key-header=api-key").run(c -> assertThat(c).hasNotFailed());
    }
    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("merchant.agent.enabled=true", "merchant.agent.base-url=http://127.0.0.1:9/v1",
                "merchant.agent.api-key=secret-test-value", "merchant.agent.model=test-model");
    }
}
