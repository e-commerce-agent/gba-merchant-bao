package com.gba.merchantbao.chat.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.concurrent.*;

/** One serialized lifecycle per accepted run. Database conditional writes decide the durable winner. */
final class ChatRunExecution implements AgentModel.Listener,AgentModel.Call {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ChatRunExecution.class);
    private final ChatService service;private final PreparedRun run;private final String requestId;
    private final AgentProperties p;private final AgentModel.Listener client;private final Runnable release;
    private final Clock clock;private final MeterRegistry metrics;
    private final StringBuilder text=new StringBuilder();
    private final long started=System.nanoTime();private long first=-1;private boolean terminal;
    private AgentModel.Call upstream;
    private java.util.List<com.gba.merchantbao.knowledge.vo.KnowledgeSource> sources=java.util.List.of();
    private Future<?> preparation;private Future<?> deadline;
    ChatRunExecution(ChatService service,PreparedRun run,String requestId,AgentProperties p,AgentModel.Listener client,
                     Runnable release,Clock clock,MeterRegistry metrics){
        this.service=service;this.run=run;this.requestId=requestId;this.p=p;this.client=client;this.release=release;this.clock=clock;this.metrics=metrics;
    }
    synchronized void bind(AgentModel.Call call){upstream=call;if(terminal)call.cancel();}
    synchronized void bindPreparation(Future<?> task){preparation=task;if(terminal&&!task.isDone())task.cancel(true);}
    synchronized void bindDeadline(Future<?> task){deadline=task;if(terminal)task.cancel(false);}
    synchronized void generate(AgentAttempts attempts,java.util.List<dev.langchain4j.data.message.ChatMessage> prompt,
                               java.util.List<com.gba.merchantbao.knowledge.vo.KnowledgeSource> sources){
        if(terminal)return;
        if(!clock.instant().isBefore(run.deadline())){end("TIMED_OUT","TIMEOUT",null);return;}
        this.sources=java.util.List.copyOf(sources);
        bind(attempts.stream(prompt,run.deadline(),this));
    }
    synchronized boolean terminal(){return terminal;}
    @Override public synchronized void delta(String delta){
        if(terminal||delta==null||delta.isEmpty())return;
        if(!clock.instant().isBefore(run.deadline())){end("TIMED_OUT","TIMEOUT",null);return;}
        int available=p.getMaxOutputChars()-text.codePointCount(0,text.length());
        int count=delta.codePointCount(0,delta.length());
        if(count>available){end("FAILED","DEPENDENCY_ERROR",null);return;}
        try {
            if(first<0){if(!service.markStreaming(run)){end("TIMED_OUT","TIMEOUT",null);return;}first=System.nanoTime();}
            text.append(delta);client.delta(delta);
        } catch(RuntimeException failure){end("FAILED","CHAT_DEPENDENCY_UNAVAILABLE",null);}
    }
    @Override public synchronized void complete(AgentModel.Usage usage){
        end(clock.instant().isBefore(run.deadline())?"COMPLETED":"TIMED_OUT",
                clock.instant().isBefore(run.deadline())?null:"TIMEOUT",usage);
    }
    @Override public synchronized void error(Throwable error){
        boolean timeout=error instanceof TimeoutException
                ||error instanceof com.gba.merchantbao.knowledge.service.ImportDeadline.ImportTimeout
                ||!clock.instant().isBefore(run.deadline());
        end(timeout?"TIMED_OUT":"FAILED",timeout?"TIMEOUT":"DEPENDENCY_ERROR",null);
    }
    @Override public synchronized void cancel(){end("CANCELLED","CANCELLED",null);}
    private void end(String status,String code,AgentModel.Usage usage){
        if(terminal)return;terminal=true;
        String reason=code==null?"completed":code;
        try {
            boolean saved=service.finish(run,status,text.toString(),code,usage,sources);
            if(saved){if(status.equals("COMPLETED")){
                if(client instanceof ChatRunListener listener)listener.complete(usage,sources);else client.complete(usage);
            }else client.error(new IllegalStateException(code));}
            else client.error(new IllegalStateException(clock.instant().isBefore(run.deadline())?"CANCELLED":"TIMEOUT"));
        } catch(RuntimeException failure){status="FAILED";reason="storage_failure";client.error(new IllegalStateException("CHAT_DEPENDENCY_UNAVAILABLE"));}
        finally {
            if(deadline!=null)deadline.cancel(false);
            if(preparation!=null&&!status.equals("COMPLETED"))preparation.cancel(true);
            if(upstream!=null)upstream.cancel();release.run();
            long total=System.nanoTime()-started,firstMs=first<0?-1:(first-started)/1_000_000;
            LOG.info("agent_run_terminal requestId={} runId={} sessionId={} merchantId={} role={} service={} promptVersion={} model={} firstTokenMs={} totalMs={} terminalStatus={} reason={}",
                    requestId,run.runId(),run.sessionId(),run.owner().merchantId(),run.owner().role(),p.getServiceLabel(),p.getPromptVersion(),p.getModel(),firstMs,total/1_000_000,status,reason);
            if(metrics!=null){metrics.counter("merchant.agent.runs","status",status).increment();
                metrics.timer("merchant.agent.duration","status",status).record(total,TimeUnit.NANOSECONDS);
                if(first>=0)metrics.timer("merchant.agent.first.token").record(first-started,TimeUnit.NANOSECONDS);}
        }
    }
}
