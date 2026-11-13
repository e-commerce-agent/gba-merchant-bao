package com.gba.merchantbao.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.knowledge.vo.KnowledgeSource;
import java.util.List;

/** Immutable JSON snapshots; history never re-resolves mutable knowledge documents. */
public final class ChatSources {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<KnowledgeSource>> TYPE = new TypeReference<>() {};
    private ChatSources() {}
    public static String encode(List<KnowledgeSource> sources) {
        try { return JSON.writeValueAsString(List.copyOf(sources)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid sources snapshot", e); }
    }
    public static List<KnowledgeSource> decode(String json) {
        if (json == null) return List.of();
        try { return List.copyOf(JSON.readValue(json, TYPE)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid sources snapshot", e); }
    }
}
