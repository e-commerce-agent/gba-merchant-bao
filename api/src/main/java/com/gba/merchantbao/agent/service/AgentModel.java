package com.gba.merchantbao.agent.service;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/** One model per deployment. Application lifecycle and persistence stay outside the SDK adapter. */
public interface AgentModel {
    Call stream(List<ChatMessage> messages, Listener listener);

    /** Explicit opt-in; existing text-only implementations remain functional interfaces. */
    default Call stream(List<ChatMessage> messages, boolean allowOrderLookup, Listener listener) {
        if (allowOrderLookup) throw new UnsupportedOperationException("Order lookup protocol is not supported");
        return stream(messages, listener);
    }

    interface Call { void cancel(); default void fail(){cancel();} }
    interface Listener {
        void delta(String text);
        void complete(Usage usage);
        void error(Throwable error);

        /** Runtime completion metadata; model adapters still report only usage. */
        default void complete(Usage usage, boolean usedOrderLookup) { complete(usage); }

        /** Terminal alternative to complete; never executes the business lookup. */
        default void toolCall(OrderLookupCall call, Usage usage) { error(new ProtocolException()); }
    }
    record Usage(Long inputTokens, Long outputTokens) { }

    /** Stable error with no supplier response, arguments or credentials attached. */
    final class ProtocolException extends RuntimeException {
        public ProtocolException() { super("Invalid Agent model protocol"); }
    }
}
