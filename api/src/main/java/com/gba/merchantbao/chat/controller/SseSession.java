package com.gba.merchantbao.chat.controller;


import com.gba.merchantbao.agent.service.AgentModel;

import com.gba.merchantbao.chat.vo.ChatRunView;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Single writer owns the servlet response; a full bounded queue cancels the upstream run. */
final class SseSession {
    private final SseEmitter emitter;private final ChatRunView run;private final String requestId;private final Duration heartbeat;
    private final ExecutorService writers;private final ScheduledExecutorService timer;private final Duration writeTimeout;
    private final AtomicBoolean ending=new AtomicBoolean();private volatile Future<?> writer;private volatile Thread writingThread;
    private volatile boolean resourceFailure;
    private final ArrayBlockingQueue<Frame> queue;private final AtomicBoolean closed=new AtomicBoolean();
    private final AtomicReference<AgentModel.Call> call=new AtomicReference<>();private final AtomicLong sequence=new AtomicLong();
    SseSession(SseEmitter emitter,ChatRunView run,String requestId,int capacity,Duration heartbeat,Duration writeTimeout,ExecutorService writers,ScheduledExecutorService timer){
        this.emitter=emitter;this.run=run;this.requestId=requestId;this.heartbeat=heartbeat;this.writeTimeout=writeTimeout;this.writers=writers;this.timer=timer;
        this.queue=new ArrayBlockingQueue<>(capacity);
        emitter.onCompletion(()->close(true));emitter.onTimeout(()->close(true));emitter.onError(error->close(true));
    }
    void start(){writer=writers.submit(this::writeLoop);if(closed.get())releaseWriter();}
    void setCall(AgentModel.Call value){if(!call.compareAndSet(null,value))throw new IllegalStateException("Call already assigned");if(closed.get()){if(resourceFailure)value.fail();else value.cancel();}}
    void startEvent(){offer("start",data("start",null,null,null,null,null,null),false);}
    AgentModel.Listener listener(){return new com.gba.merchantbao.chat.service.ChatRunListener(){
        public void delta(String delta){offer("token",data("token",delta,null,null,null,null,null),false);}
        public void complete(AgentModel.Usage usage,java.util.List<com.gba.merchantbao.knowledge.vo.KnowledgeSource> sources){
            complete(usage,sources,java.util.List.of());
        }
        public void complete(AgentModel.Usage usage,java.util.List<com.gba.merchantbao.knowledge.vo.KnowledgeSource> sources,
                             java.util.List<com.gba.merchantbao.chat.vo.ToolCallSummary> toolCalls){
            var done=data("done",null,"COMPLETED",usage,null,null,null);done.put("sources",sources);done.put("toolCalls",toolCalls);offer("done",done,true);
        }
        public void error(Throwable error){
            String code=error.getMessage();if(code==null||!Set.of("TIMEOUT","DEPENDENCY_ERROR","CHAT_DEPENDENCY_UNAVAILABLE","CANCELLED","INTERNAL_ERROR").contains(code))code="DEPENDENCY_ERROR";
            String terminal=code.equals("TIMEOUT")?"TIMED_OUT":code.equals("CANCELLED")?"CANCELLED":"FAILED";
            SseSession.this.error(code,safeMessage(code),terminal);
        }
    };}
    void error(String code,String message,String terminal){offer("error",data("error",null,null,null,code,message,terminal),true);}
    private Map<String,Object> data(String event,String delta,String status,AgentModel.Usage usage,String code,String message,String terminal) {
        var m=new LinkedHashMap<String,Object>();m.put("version","v1");m.put("runId",run.runId().asString());m.put("sessionId",run.sessionId().asString());
        m.put("messageId",run.messageId().asString());m.put("requestId",requestId);
        if(event.equals("start"))m.put("userMessageId",run.userMessageId().asString());
        if(event.equals("token"))m.put("delta",delta);
        if(event.equals("done")){m.put("status",status);if(usage==null)m.put("usage",null);else{var u=new LinkedHashMap<String,Long>();u.put("inputTokens",usage.inputTokens());u.put("outputTokens",usage.outputTokens());m.put("usage",u);}}
        if(event.equals("error")){m.put("code",code);m.put("message",message);m.put("terminalStatus",terminal);}
        return m;
    }
    private String safeMessage(String code){return switch(code){case "TIMEOUT"->"模型响应超时";case "CANCELLED"->"请求已取消";case "CHAT_DEPENDENCY_UNAVAILABLE"->"对话状态暂不可用";default->"模型服务不可用";};}
    private void offer(String name,Map<String,Object> data,boolean terminal){
        synchronized(queue){
            if(closed.get()||ending.get())return;
            if(terminal)ending.set(true);
            if(!queue.offer(new Frame(name,data,sequence.incrementAndGet(),terminal))){resourceFailure=true;close(true);}
        }
    }
    private void writeLoop(){
        writingThread=Thread.currentThread();
        try {
            while(!closed.get()) {
                Frame f=queue.poll(Math.max(1,heartbeat.toMillis()),TimeUnit.MILLISECONDS);
                var deadline=timer.schedule(()->{resourceFailure=true;close(true);},writeTimeout.toMillis(),TimeUnit.MILLISECONDS);
                try {
                    if(f==null){emitter.send(SseEmitter.event().comment("heartbeat"));continue;}
                    emitter.send(SseEmitter.event().name(f.name()).id(run.runId().asString()+":"+f.seq())
                            .data(f.data(),MediaType.APPLICATION_JSON));
                } finally {deadline.cancel(false);}
                if(f.terminal()){if(closed.compareAndSet(false,true))emitter.complete();return;}
            }
        } catch(IOException|IllegalStateException e){close(true);}
        catch(InterruptedException e){Thread.currentThread().interrupt();close(true);}
        finally {writingThread=null;}
    }
    private void close(boolean cancel){
        if(!closed.compareAndSet(false,true))return;
        queue.clear();var c=call.get();
        try {if(cancel&&c!=null){if(resourceFailure)c.fail();else c.cancel();}}
        finally {
            releaseWriter();
            emitter.completeWithError(new IOException("Stream closed"));
        }
    }
    private void releaseWriter(){var future=writer;if(future!=null){future.cancel(true);if(writers instanceof ThreadPoolExecutor pool)pool.remove((Runnable)future);}}
    private record Frame(String name,Map<String,Object> data,long seq,boolean terminal) { }
}
