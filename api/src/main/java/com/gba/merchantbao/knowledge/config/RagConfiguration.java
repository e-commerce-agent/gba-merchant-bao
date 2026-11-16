package com.gba.merchantbao.knowledge.config;

import com.gba.merchantbao.knowledge.service.MilvusKnowledgeStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {
    @Bean(destroyMethod = "close")
    MilvusKnowledgeStore knowledgeVectors(RagProperties properties) {
        properties.validate();
        var store = new MilvusKnowledgeStore(properties);
        if (properties.isEnabled()) {
            try { store.initialize(); }
            catch (RuntimeException e) { store.close(); throw new IllegalStateException("RAG collection initialization failed", e); }
        }
        return store;
    }
}
