package com.gba.merchantbao.knowledge.service;

import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.mapper.KnowledgeMapper;
import com.gba.merchantbao.knowledge.vo.KnowledgeSource;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeRetrieval {
    public static final String NO_MATCH = "当前资料中没有找到相关信息，请补充说明或联系店铺确认";
    private final RagProperties properties;
    private final KnowledgeEmbedding embedding;
    private final MilvusKnowledgeStore vectors;
    private final KnowledgeMapper documents;
    public KnowledgeRetrieval(RagProperties properties, KnowledgeEmbedding embedding,
                              MilvusKnowledgeStore vectors, KnowledgeMapper documents) {
        this.properties = properties; this.embedding = embedding; this.vectors = vectors; this.documents = documents;
    }
    public boolean enabled() { return properties.isEnabled(); }

    /** Call only after acceptance, with the merchant from the trusted run owner. Failures propagate. */
    public List<KnowledgeSource> retrieve(long merchantId, String question, ImportDeadline deadline) {
        deadline.remaining();
        var vector = embedding.embed(List.of(question), deadline).getFirst();
        deadline.remaining();
        var hits = vectors.search(merchantId, vector, deadline).stream()
                .filter(hit -> Float.isFinite(hit.score()) && hit.score() >= properties.getMinScore())
                .sorted(Comparator.comparingDouble(MilvusKnowledgeStore.Hit::score).reversed())
                .limit(properties.getTopK()).toList();
        deadline.remaining();
        if (hits.isEmpty()) return List.of();
        var rows = documents.readyReferences(merchantId, hits.stream().map(MilvusKnowledgeStore.Hit::chunkId).distinct().toList())
                .stream().collect(Collectors.toMap(c -> c.chunkId(), c -> c));
        deadline.remaining();
        var selected = new LinkedHashMap<Long, KnowledgeSource>();
        for (var hit : hits) {
            var row = rows.get(hit.chunkId());
            if (row == null || row.documentId() != hit.documentId()) continue;
            selected.putIfAbsent(row.chunkId(), new KnowledgeSource(new ApiId(row.documentId()), new ApiId(row.chunkId()),
                    row.title(), row.chunkNo(), row.content()));
        }
        return List.copyOf(selected.values());
    }
}
