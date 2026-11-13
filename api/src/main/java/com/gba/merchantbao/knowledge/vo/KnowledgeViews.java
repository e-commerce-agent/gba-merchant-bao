package com.gba.merchantbao.knowledge.vo;

import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.knowledge.entity.KnowledgeDocument;
import java.time.*;
import java.util.List;

public final class KnowledgeViews {
    private KnowledgeViews() { }
    public record Document(ApiId documentId, String title, String status, int chunkCount, Instant createdAt) {
        public static Document from(KnowledgeDocument row) {
            return new Document(new ApiId(row.id()), row.title(), row.status(), row.chunkCount(), row.createdAt().toInstant(ZoneOffset.UTC));
        }
    }
    public record Page(List<Document> items, String nextCursor, boolean hasMore) { }
}
