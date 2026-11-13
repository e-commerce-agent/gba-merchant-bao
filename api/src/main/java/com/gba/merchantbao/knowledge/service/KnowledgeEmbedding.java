package com.gba.merchantbao.knowledge.service;

import com.gba.merchantbao.knowledge.config.RagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeEmbedding {
    private final RagProperties properties;
    public KnowledgeEmbedding(RagProperties properties) { this.properties = properties; }
    public List<List<Float>> embed(List<String> texts, ImportDeadline deadline) {
        var result = new ArrayList<List<Float>>();
        // Small batches bound provider input; max chunk is 300 codepoints (<=1200 UTF-8 bytes).
        for (int start = 0; start < texts.size(); start += 16) {
            var model = OpenAiEmbeddingModel.builder().baseUrl(properties.getEmbeddingBaseUrl())
                    .apiKey(properties.getEmbeddingApiKey()).modelName(properties.getEmbeddingModel())
                    .dimensions(properties.getEmbeddingDimension()).timeout(deadline.remaining()).maxRetries(0)
                    .maxSegmentsPerBatch(16).logRequests(false).logResponses(false).build();
            var batch = texts.subList(start, Math.min(start + 16, texts.size()));
            var embeddings = model.embedAll(batch.stream().map(TextSegment::from).toList()).content();
            deadline.remaining();
            if (embeddings.size() != batch.size()) throw new IllegalStateException("Invalid embedding count");
            for (var embedding : embeddings) {
                float[] vector = embedding.vector();
                if (vector.length != properties.getEmbeddingDimension()) throw new IllegalStateException("Invalid embedding dimension");
                var values = new ArrayList<Float>(vector.length);
                double norm = 0;
                for (float v : vector) {
                    if (!Float.isFinite(v)) throw new IllegalStateException("Invalid embedding value");
                    values.add(v); norm += (double) v * v;
                }
                if (norm == 0) throw new IllegalStateException("Empty embedding");
                result.add(List.copyOf(values));
            }
        }
        return List.copyOf(result);
    }
}
