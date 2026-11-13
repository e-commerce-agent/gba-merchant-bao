package com.gba.merchantbao.chat.entity;

import java.time.LocalDateTime;

/** Persistence only; never log message content or expose this row as an API response. */
public record ChatMessageRow(long id, long merchantId, long sessionId, long messageSeq, String senderType,
                             Long senderAccountId, String clientMessageId, String content, String status,
                             long runId, String errorCode, String promptVersion, String modelServiceLabel,
                             String modelName, Long inputTokens, Long outputTokens,
                             LocalDateTime createdAt, LocalDateTime completedAt, String sourcesJson) {
    @org.apache.ibatis.annotations.AutomapConstructor
    public ChatMessageRow {}
    public ChatMessageRow(long id,long merchantId,long sessionId,long messageSeq,String senderType,
                          Long senderAccountId,String clientMessageId,String content,String status,long runId,
                          String errorCode,String promptVersion,String modelServiceLabel,String modelName,
                          Long inputTokens,Long outputTokens,LocalDateTime createdAt,LocalDateTime completedAt) {
        this(id,merchantId,sessionId,messageSeq,senderType,senderAccountId,clientMessageId,content,status,runId,
                errorCode,promptVersion,modelServiceLabel,modelName,inputTokens,outputTokens,createdAt,completedAt,null);
    }
    @Override public String toString() { return "ChatMessageRow[id=" + id + ", merchantId=" + merchantId + "]"; }
}
