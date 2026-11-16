package com.gba.merchantbao.knowledge.entity;

/** Authoritative text and title read together behind the merchant/READY gate. */
public record ReadyKnowledgeChunk(long documentId, long chunkId, String title, int chunkNo, String content) {
    @Override public String toString() { return "ReadyKnowledgeChunk[chunkId=" + chunkId + "]"; }
}
