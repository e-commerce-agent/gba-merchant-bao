package com.gba.merchantbao.knowledge.entity;

import java.time.LocalDateTime;

public record KnowledgeDocument(long id, long merchantId, String title, String status,
                                int chunkCount, long createdBy, LocalDateTime createdAt) { }
