package com.gba.merchantbao.agent.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** SDK owns request serialization and SSE parsing; each call owns a cancellable transport. */
public final class OpenAiAgentModel implements AgentModel {
    private final AgentProperties properties;
    public OpenAiAgentModel(AgentProperties properties) { this.properties = properties; }

    @Override public Call stream(List<ChatMessage> messages, Listener listener) {
        return stream(messages, false, listener);
    }

    @Override public Call stream(List<ChatMessage> messages, boolean allowOrderLookup, Listener listener) {
        var ended = new AtomicBoolean();
        var handle = new AtomicReference<StreamingHandle>();
        var transport = new OwnedHttpClientBuilder();
        boolean bearer = "Authorization".equalsIgnoreCase(properties.getApiKeyHeader());
        var model = OpenAiStreamingChatModel.builder().baseUrl(properties.getBaseUrl())
                .apiKey(bearer ? properties.getApiKey() : null)
                .customHeaders(bearer ? Map.of() : Map.of(properties.getApiKeyHeader(), properties.getApiKey()))
                .modelName(properties.getModel()).temperature(properties.getTemperature())
                .maxTokens(properties.getMaxOutputTokens()).timeout(properties.getTotalTimeout())
                .parallelToolCalls(allowOrderLookup ? false : null).strictTools(allowOrderLookup)
                .httpClientBuilder(JdkHttpClient.builder().httpClientBuilder(transport)
                        .connectTimeout(properties.getConnectTimeout()).readTimeout(properties.getTotalTimeout()))
                .logRequests(false).logResponses(false).build();
        var request = ChatRequest.builder().messages(messages);
        if (allowOrderLookup) request.toolSpecifications(OrderLookupCall.SPECIFICATION);
        model.chat(request.build(), new StreamingChatResponseHandler() {
            private final StringBuilder selectionText = new StringBuilder();
            private int argumentChars;
            private int toolCalls;

            private boolean active(StreamingHandle current) {
                handle.set(current);
                if (ended.get()) { current.cancel(); return false; }
                return true;
            }

            private void fail(Throwable error) {
                if (!ended.compareAndSet(false, true)) return;
                try { listener.error(safeError(error)); }
                finally { var h = handle.get(); if (h != null) h.cancel(); transport.stop(); }
            }

            @Override public void onPartialResponse(PartialResponse response, PartialResponseContext context) {
                if (!active(context.streamingHandle())) return;
                if (allowOrderLookup) {
                    if ((long) selectionText.length() + response.text().length() > properties.getMaxOutputChars()) {
                        fail(new ProtocolException()); return;
                    }
                    selectionText.append(response.text());
                } else listener.delta(response.text());
            }

            @Override public void onPartialToolCall(PartialToolCall call, PartialToolCallContext context) {
                if (!active(context.streamingHandle())) return;
                argumentChars += call.partialArguments().length();
                if (!allowOrderLookup || call.index() != 0 || argumentChars > OrderLookupCall.MAX_ARGUMENT_CHARS)
                    fail(new ProtocolException());
            }

            @Override public void onCompleteToolCall(CompleteToolCall call) {
                if (ended.get()) return;
                // Count SDK callbacks as well: some SDK versions retain only the first call per chunk.
                if (!allowOrderLookup || ++toolCalls > 1 || call.index() != 0) fail(new ProtocolException());
            }

            @Override public void onCompleteResponse(ChatResponse response) {
                if (ended.get()) return;
                OrderLookupCall lookup = null;
                try {
                    if (response.aiMessage() == null) throw new ProtocolException();
                    if (response.aiMessage().hasToolExecutionRequests()) {
                        if (!allowOrderLookup || toolCalls != 1 || response.finishReason() != FinishReason.TOOL_EXECUTION)
                            throw new ProtocolException();
                        lookup = OrderLookupCall.parse(response.aiMessage());
                    } else if (toolCalls != 0 || response.finishReason() == FinishReason.TOOL_EXECUTION) {
                        throw new ProtocolException();
                    }
                } catch (RuntimeException invalid) { fail(new ProtocolException()); return; }
                if (!ended.compareAndSet(false, true)) return;
                try {
                    var u = response.tokenUsage();
                    Usage usage = u == null || (u.inputTokenCount() == null && u.outputTokenCount() == null)
                            ? null : new Usage(u.inputTokenCount() == null ? null : u.inputTokenCount().longValue(),
                            u.outputTokenCount() == null ? null : u.outputTokenCount().longValue());
                    if (lookup != null) listener.toolCall(lookup, usage);
                    else {
                        if (allowOrderLookup && !selectionText.isEmpty()) listener.delta(selectionText.toString());
                        listener.complete(usage);
                    }
                } finally { transport.stop(); }
            }

            @Override public void onError(Throwable error) { fail(error); }
        });
        return () -> { ended.set(true); var h = handle.get(); if (h != null) h.cancel(); transport.stop(); };
    }

    private static Throwable safeError(Throwable error) {
        // Preserve only retry classification, never supplier bodies, URLs, credentials or raw causes.
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http)
                return new HttpException(http.statusCode(), "Agent model HTTP failure");
            if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) return new ProtocolException();
            if (cause instanceof java.io.IOException) return new java.io.IOException("Agent model transport failure");
        }
        return new ProtocolException();
    }
}
