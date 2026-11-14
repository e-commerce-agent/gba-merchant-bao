package com.gba.merchantbao.agent.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/** Validated protocol data, held only in the current model exchange. Contains no Actor identity. */
public final class OrderLookupCall {
    public static final String NAME = "lookup_order";
    static final int MAX_ARGUMENT_CHARS = 1024;
    static final ToolSpecification SPECIFICATION = ToolSpecification.builder()
            .name(NAME).description("按订单号只读查询当前已认证用户有权访问的订单详情")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("orderNo", "订单号：1–64 个 ASCII 字符，首位字母或数字，后续允许字母、数字、下划线、连字符")
                    .required("orderNo").additionalProperties(false).build())
            .build();
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final AiMessage assistantMessage;
    private final String orderNo;

    private OrderLookupCall(AiMessage assistantMessage, String orderNo) {
        this.assistantMessage = assistantMessage;
        this.orderNo = orderNo;
    }

    static OrderLookupCall parse(AiMessage message) {
        var requests = message.toolExecutionRequests();
        if (requests == null || requests.size() != 1) throw new AgentModel.ProtocolException();
        var request = requests.getFirst();
        if (!NAME.equals(request.name()) || request.id() == null || request.id().isBlank()
                || request.id().length() > 128 || request.arguments() == null
                || request.arguments().length() > MAX_ARGUMENT_CHARS) throw new AgentModel.ProtocolException();
        try {
            var arguments = JSON.readTree(request.arguments());
            if (arguments == null || !arguments.isObject() || arguments.size() != 1
                    || !arguments.path("orderNo").isTextual()) throw new AgentModel.ProtocolException();
            String orderNo = arguments.path("orderNo").textValue();
            if (!orderNo.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw new AgentModel.ProtocolException();
            return new OrderLookupCall(message, orderNo);
        } catch (java.io.IOException invalidJson) {
            // Do not attach Jackson's exception: its message can contain raw arguments.
            throw new AgentModel.ProtocolException();
        }
    }

    public String orderNo() { return orderNo; }
    public AiMessage assistantMessage() { return assistantMessage; }

    /** Pair this result with assistantMessage() in the next request, preserving the supplier call id. */
    public ToolExecutionResultMessage toolResult(String resultJson) {
        return ToolExecutionResultMessage.from(assistantMessage.toolExecutionRequests().getFirst(), resultJson);
    }

    @Override public String toString() { return "OrderLookupCall[lookup_order]"; }
}
