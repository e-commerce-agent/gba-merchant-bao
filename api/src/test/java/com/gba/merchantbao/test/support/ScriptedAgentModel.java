package com.gba.merchantbao.test.support;

import com.gba.merchantbao.agent.service.AgentModel;
import dev.langchain4j.data.message.ChatMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic model boundary for runtime/HTTP tests; no network or provider credentials. */
public final class ScriptedAgentModel implements AgentModel, AutoCloseable {
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
    volatile List<String> deltas=List.of("你好，","请问有什么可以帮你？");
    public volatile Throwable failure;
    public volatile Duration delay=Duration.ZERO;
    final AtomicInteger calls=new AtomicInteger();
    public final AtomicInteger cancellations=new AtomicInteger();
    volatile List<ChatMessage> lastMessages;
    @Override public Call stream(List<ChatMessage> messages,Listener listener) {
        calls.incrementAndGet(); lastMessages=List.copyOf(messages); var cancelled=new AtomicBoolean();
        var future=executor.schedule(()->{
            for(String d:deltas) { if(cancelled.get())return; listener.delta(d); }
            if(cancelled.get())return;
            if(failure!=null)listener.error(failure); else listener.complete(null);
        },delay.toMillis(),TimeUnit.MILLISECONDS);
        return ()->{if(cancelled.compareAndSet(false,true))cancellations.incrementAndGet();future.cancel(true);};
    }
    @Override public void close(){executor.shutdownNow();}
}
