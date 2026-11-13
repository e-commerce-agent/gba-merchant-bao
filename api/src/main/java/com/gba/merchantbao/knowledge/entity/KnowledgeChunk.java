package com.gba.merchantbao.knowledge.entity;

import java.time.LocalDateTime;

public record KnowledgeChunk(long id, long merchantId, long documentId, int chunkNo,
                             String content, LocalDateTime createdAt) {
    @Override public String toString() { return "KnowledgeChunk[id=" + id + "]"; }
}
