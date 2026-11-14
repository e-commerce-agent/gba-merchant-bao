package com.gba.merchantbao.agent.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.HttpException;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/** One deadline, retry allowance and cancellation scope across model rounds and the single lookup. */
@Component
public final class AgentAttempts {
    private final AgentModel model;
    private final AgentProperties properties;
    private final ScheduledExecutorService timer;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentAttempts(AgentModel model, AgentProperties properties, Clock clock) {
        this(model, properties, timer(), clock);
    }
    public AgentAttempts(AgentModel model, AgentProperties properties, ScheduledExecutorService timer, Clock clock) {
        this.model=model; this.properties=properties; this.timer=timer; this.clock=clock;
    }
    private static ScheduledExecutorService timer() {
        var timer=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"agent-retry-timer");t.setDaemon(true);return t;});
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }
    public AgentModel.Call stream(List<ChatMessage> context, Instant deadline, AgentModel.Listener downstream) {
        return stream(context,deadline,null,null,downstream);
    }
    public AgentModel.Call stream(List<ChatMessage> context, Instant deadline, Function<OrderLookupCall,String> lookup,
                                  ExecutorService worker, AgentModel.Listener downstream) {
        var stopped=new AtomicBoolean();
        var abortWork=new AtomicBoolean();
        var body=new AtomicBoolean();
        var usedTool=new AtomicBoolean();
        var retries=new AtomicInteger();
        var calls=new CopyOnWriteArrayList<AgentModel.Call>();
        var scheduled=new CopyOnWriteArrayList<Future<?>>();
        var lookupTask=new AtomicReference<Future<?>>();
        var lookupThread=new AtomicReference<Thread>();
        class Execution {
            volatile List<ChatMessage> prompt=List.copyOf(context);
            volatile AgentModel.Usage selectionUsage;
            void cleanup(boolean interruptWork) {
                scheduled.forEach(f->f.cancel(false));
                var task=lookupTask.get();
                if(interruptWork&&task!=null)task.cancel(lookupThread.get()!=Thread.currentThread());
                calls.forEach(AgentModel.Call::cancel);
            }
            void fail(Throwable error) {
                if(stopped.compareAndSet(false,true)) {
                    abortWork.set(true);cleanup(true);downstream.error(error);
                }
            }
            boolean active() {
                if(stopped.get())return false;
                if(!clock.instant().isBefore(deadline)){fail(new TimeoutException("Agent response timed out"));return false;}
                return true;
            }
            void track(Future<?> future) {
                scheduled.add(future);
                if(stopped.get())future.cancel(true);
            }
            void schedule(Runnable action,long delay) { track(timer.schedule(action,delay,TimeUnit.MILLISECONDS)); }
            void lookup(OrderLookupCall call) {
                if(!active())return;
                try {
                    String result=lookup.apply(call);
                    if(!active())return;
                    var next=new ArrayList<>(prompt);
                    next.add(call.assistantMessage());next.add(call.toolResult(result));
                    // Reject oversized tool data rather than silently truncating order facts.
                    long bytes=0;
                    for(var message:next)bytes+=messageCost(message);
                    if(bytes+properties.getMaxOutputTokens()>properties.getContextTokens())
                        throw new AgentModel.ProtocolException();
                    prompt=List.copyOf(next);
                    start();
                } catch(RuntimeException error) { fail(error); }
            }
            void start() {
                if(!active())return;
                boolean allowLookup=lookup!=null&&!usedTool.get();
                var ended=new AtomicBoolean();
                var handle=new AtomicReference<AgentModel.Call>();
                AgentModel.Listener listener=new AgentModel.Listener() {
                    public void delta(String text) {
                        if(!ended.get()&&active()&&text!=null&&!text.isEmpty()) {
                            body.set(true);downstream.delta(text);
                        }
                    }
                    public void complete(AgentModel.Usage usage) {
                        if(ended.compareAndSet(false,true)&&active()&&stopped.compareAndSet(false,true)) {
                            cleanup(false);downstream.complete(add(selectionUsage,usage),usedTool.get());
                        }
                    }
                    public void toolCall(OrderLookupCall call,AgentModel.Usage usage) {
                        if(!ended.compareAndSet(false,true)||!active())return;
                        if(!allowLookup||body.get()||call==null||!usedTool.compareAndSet(false,true)) {
                            fail(new AgentModel.ProtocolException());return;
                        }
                        selectionUsage=usage;
                        var current=handle.get();if(current!=null)current.cancel();
                        try {
                            var task=worker.submit(()->{
                                lookupThread.set(Thread.currentThread());
                                try{Execution.this.lookup(call);}finally{lookupThread.set(null);}
                            });
                            lookupTask.set(task);
                            if(abortWork.get())task.cancel(lookupThread.get()!=Thread.currentThread());
                        }
                        catch(RuntimeException rejected) { fail(rejected); }
                    }
                    public void error(Throwable error) {
                        if(!ended.compareAndSet(false,true)||!active())return;
                        var current=handle.get();if(current!=null)current.cancel();
                        if(!body.get()&&retryable(error)&&retries.getAndIncrement()<properties.getMaxRetries()
                                &&clock.instant().plus(properties.getRetryBackoff()).isBefore(deadline)) {
                            schedule(Execution.this::start,properties.getRetryBackoff().toMillis());
                        } else fail(error);
                    }
                };
                try {
                    var call=lookup==null?model.stream(prompt,listener):model.stream(prompt,allowLookup,listener);
                    handle.set(call);calls.add(call);
                    if(stopped.get()||ended.get())call.cancel();
                } catch(RuntimeException error) { listener.error(error); }
            }
        }
        var execution=new Execution();
        long remaining=Math.max(1,Duration.between(clock.instant(),deadline).toMillis());
        execution.schedule(()->execution.fail(new TimeoutException("Agent response timed out")),remaining);
        execution.schedule(()->{if(!body.get())execution.fail(new TimeoutException("Agent response timed out"));},
                Math.min(properties.getFirstTokenTimeout().toMillis(),remaining));
        execution.start();
        return ()->{
            if(stopped.compareAndSet(false,true)){abortWork.set(true);execution.cleanup(true);}
            else execution.cleanup(false);
        };
    }
    private static long messageCost(ChatMessage message) {
        if(message instanceof dev.langchain4j.data.message.SystemMessage m)return bytes(m.text())+4;
        if(message instanceof dev.langchain4j.data.message.UserMessage m)return bytes(m.singleText())+4;
        if(message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage m)return bytes(m.text())+bytes(m.id())+bytes(m.toolName())+4;
        var m=(dev.langchain4j.data.message.AiMessage)message;
        long cost=bytes(m.text())+4;
        if(m.hasToolExecutionRequests())for(var call:m.toolExecutionRequests())cost+=bytes(call.id())+bytes(call.name())+bytes(call.arguments())+4;
        return cost;
    }
    private static int bytes(String text){return text==null?0:text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static AgentModel.Usage add(AgentModel.Usage first,AgentModel.Usage last) {
        if(first==null)return last;if(last==null)return first;
        return new AgentModel.Usage(sum(first.inputTokens(),last.inputTokens()),sum(first.outputTokens(),last.outputTokens()));
    }
    private static Long sum(Long a,Long b){return a==null||b==null?null:a+b;}
    private static boolean retryable(Throwable error) {
        if(error instanceof HttpException http)return http.statusCode()==429||(http.statusCode()>=500&&http.statusCode()<600);
        for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof java.io.IOException)return true;
        return false;
    }
    @jakarta.annotation.PreDestroy void shutdown(){timer.shutdownNow();}
}
