package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.knowledge.config.*;
import com.gba.merchantbao.knowledge.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RagConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RagConfiguration.class);
    @Test void disabledDoesNotConnectOrNeedCredentials() {
        runner.withPropertyValues("merchant.rag.milvus-uri=http://127.0.0.1:1")
                .run(c -> { assertThat(c).hasNotFailed(); assertThat(c).hasSingleBean(MilvusKnowledgeStore.class); });
    }
    @Test void enabledRejectsMissingCredentialsBeforeConnecting() {
        runner.withPropertyValues("merchant.rag.enabled=true").run(c -> assertThat(c).hasFailed());
        var p = new RagProperties(); p.setEnabled(true); p.setEmbeddingApiKey("test-key");
        p.setEmbeddingBaseUrl("http://localhost/v1"); p.validate();
        p.setEmbeddingDimension(0); assertThatThrownBy(p::validate).hasMessageContaining("embedding-dimension");
    }
}
