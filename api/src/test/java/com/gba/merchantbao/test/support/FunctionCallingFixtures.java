package com.gba.merchantbao.agent.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

/** Deterministic validated SDK protocol value for runtime tests. */
public final class FunctionCallingFixtures {
    private FunctionCallingFixtures() { }
    public static OrderLookupCall orderCall() {
        return OrderLookupCall.parse(AiMessage.from(ToolExecutionRequest.builder()
                .id("call_test").name(OrderLookupCall.NAME).arguments("{\"orderNo\":\"SO-1\"}").build()));
    }
}
