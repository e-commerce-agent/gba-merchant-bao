package com.gba.merchantbao.chat.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.chat.vo.ToolCallSummary;
import java.util.List;

/** Closed, bounded snapshot format: never persists model-provided arguments or results. */
public final class ChatToolCalls {
    private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<ToolCallSummary> SUCCESS=List.of(new ToolCallSummary("lookup_order","SUCCEEDED"));
    private ChatToolCalls() {}
    public static List<ToolCallSummary> orderLookupSucceeded(){return SUCCESS;}
    public static String encode(List<ToolCallSummary> calls) {
        if(calls==null||calls.size()>1||!calls.isEmpty()&&!SUCCESS.equals(calls))throw invalid();
        try{return JSON.writeValueAsString(List.copyOf(calls));}
        catch(com.fasterxml.jackson.core.JsonProcessingException error){throw invalid();}
    }
    public static List<ToolCallSummary> decode(String json) {
        if(json==null)return List.of();
        if(json.length()>256)throw invalid();
        try {
            var array=JSON.readTree(json);
            if(array==null||!array.isArray()||array.size()>1)throw invalid();
            if(array.isEmpty())return List.of();
            var item=array.get(0);
            if(!item.isObject()||item.size()!=2||!"lookup_order".equals(item.path("name").textValue())
                    ||!"SUCCEEDED".equals(item.path("status").textValue()))throw invalid();
            return SUCCESS;
        } catch(java.io.IOException error){throw invalid();}
    }
    private static IllegalStateException invalid(){return new IllegalStateException("Invalid tool summary snapshot");}
}
