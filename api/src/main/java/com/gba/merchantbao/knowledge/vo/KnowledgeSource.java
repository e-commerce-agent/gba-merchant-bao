package com.gba.merchantbao.knowledge.vo;

import com.gba.merchantbao.common.api.ApiId;

/** Snapshot of a reference actually included in the model input, not a verified citation. */
public record KnowledgeSource(ApiId documentId, ApiId chunkId, String title, int chunkNo, String excerpt) { }
