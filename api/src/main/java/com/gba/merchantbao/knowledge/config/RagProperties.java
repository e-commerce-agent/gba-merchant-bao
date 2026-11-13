package com.gba.merchantbao.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@Getter @Setter
@ConfigurationProperties("merchant.rag")
public class RagProperties {
    private boolean enabled;
    private String embeddingBaseUrl, embeddingApiKey;
    private String embeddingModel = "text-embedding-3-small";
    private int embeddingDimension = 1536;
    private String milvusUri = "http://127.0.0.1:19530";
    private String milvusToken = "";
    private String collection = "knowledge_text_embedding_3_small";
    private int topK = 3;
    private double minScore = 0.5;

    public void validate() {
        if (!enabled) return;
        require(url(embeddingBaseUrl) && url(milvusUri), "URL");
        require(embeddingApiKey != null && !embeddingApiKey.isBlank(), "embedding-api-key");
        require(embeddingModel != null && embeddingModel.matches("[A-Za-z0-9._:/-]{1,128}"), "embedding-model");
        require(embeddingDimension >= 2 && embeddingDimension <= 32768, "embedding-dimension");
        require(collection != null && collection.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"), "collection");
        require(topK >= 1 && topK <= 5 && Double.isFinite(minScore) && minScore >= -1 && minScore <= 1, "retrieval-limits");
    }
    private static boolean url(String value) {
        try {
            var u = URI.create(value);
            return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null
                    && u.getUserInfo() == null && u.getQuery() == null && u.getFragment() == null;
        } catch (RuntimeException e) { return false; }
    }
    private static void require(boolean ok, String field) {
        if (!ok) throw new IllegalStateException("Invalid merchant.rag " + field);
    }
}
