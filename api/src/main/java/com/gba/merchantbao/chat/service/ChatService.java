package com.gba.merchantbao.chat.service;

import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.chat.entity.*;
import com.gba.merchantbao.chat.mapper.*;
import com.gba.merchantbao.chat.exception.ChatError;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.common.id.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;

@Service
public class ChatService {
    private final ChatSessionMapper sessions;
    private final ChatMessageMapper messages;
    private final IdGenerator ids;
    private final Clock clock;
    private final AgentProperties p;
    private final TransactionTemplate tx;
    private final com.gba.merchantbao.agent.service.AgentContext context;
    private final com.gba.merchantbao.agent.service.AgentAttempts attempts;
    private final java.util.concurrent.Semaphore admission;
    private final java.util.Set<ChatRunExecution> active=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private io.micrometer.core.instrument.MeterRegistry metrics;
    private final java.util.concurrent.ThreadPoolExecutor runtime;
    private com.gba.merchantbao.knowledge.service.KnowledgeRetrieval retrieval;
    private final java.util.concurrent.ScheduledExecutorService deadlines=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{
        var t=new Thread(r,"chat-run-deadline");t.setDaemon(true);return t;
    });
    public ChatService(ChatSessionMapper sessions,ChatMessageMapper messages,IdGenerator ids,Clock clock,AgentProperties p,PlatformTransactionManager manager) {
        this(sessions,messages,ids,clock,p,manager,null,null);
    }
    public ChatService(ChatSessionMapper sessions,ChatMessageMapper messages,IdGenerator ids,Clock clock,AgentProperties p,
                       PlatformTransactionManager manager,com.gba.merchantbao.agent.service.AgentAttempts attempts) {
        this(sessions,messages,ids,clock,p,manager,attempts,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ChatService(ChatSessionMapper sessions,ChatMessageMapper messages,IdGenerator ids,Clock clock,AgentProperties p,
                       PlatformTransactionManager manager,com.gba.merchantbao.agent.service.AgentAttempts attempts,
                       com.gba.merchantbao.agent.service.AgentModel agentModel) {
        this.sessions=sessions;this.messages=messages;this.ids=ids;this.clock=clock;this.p=p;this.tx=new TransactionTemplate(manager);
        this.context=new com.gba.merchantbao.agent.service.AgentContext(p);
        this.attempts=attempts;
        this.admission=new java.util.concurrent.Semaphore(p.getWorkers()+p.getQueueCapacity());
        this.runtime=new java.util.concurrent.ThreadPoolExecutor(p.getWorkers(),p.getWorkers(),0,java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(p.getQueueCapacity()),r->{var t=new Thread(r,"agent-runtime");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    }
    @org.springframework.beans.factory.annotation.Autowired
    void metrics(io.micrometer.core.instrument.MeterRegistry registry){this.metrics=registry;}
    @org.springframework.beans.factory.annotation.Autowired
    void retrieval(com.gba.merchantbao.knowledge.service.KnowledgeRetrieval retrieval){this.retrieval=retrieval;}
    public static ChatOwner owner(Actor actor) {
        if(actor==null)throw new ChatError(401,com.gba.merchantbao.common.error.CommonErrorCode.UNAUTHORIZED);
        var a=actor.principal(); return new ChatOwner(a.merchantId(),a.accountId(),a.role().name());
    }
    public ChatSessionRow create(Actor actor,String title) {
        var o=owner(actor); String t=title==null || title.strip().isBlank()?"新对话":title.strip();
        if(t.codePointCount(0,t.length())>200)throw ChatError.invalid();
        var now=now(); var row=new ChatSessionRow(ids.nextId(),o.merchantId(),o.accountId(),o.role(),t,"ACTIVE",now,now,null,null,1,null,null);
        sessions.insert(row);return row;
    }
    public ChatSessionRow detail(Actor actor,long sessionId){var o=owner(actor);return tx.execute(s->owned(o,sessionId,true));}
    public String activeRunStatus(Actor actor,ChatSessionRow row){
        if(row.activeRunId()==null)return null;
        var assistant=messages.findAssistantByRun(owner(actor),row.id(),row.activeRunId());
        return assistant==null?null:assistant.status();
    }
    /** Returns only after the terminal transaction commits. False means this run has lost ownership. */
    public boolean finish(PreparedRun run,String status,String text,String error,com.gba.merchantbao.agent.service.AgentModel.Usage usage) {
        return finish(run,status,text,error,usage,java.util.List.of());
    }
    public boolean finish(PreparedRun run,String status,String text,String error,com.gba.merchantbao.agent.service.AgentModel.Usage usage,
                          java.util.List<com.gba.merchantbao.knowledge.vo.KnowledgeSource> sources) {
        if(!java.util.Set.of("COMPLETED","CANCELLED","TIMED_OUT","FAILED").contains(status))throw ChatError.invalid();
        return Boolean.TRUE.equals(tx.execute(s->{
            var session=sessions.lockOwned(run.owner(),run.sessionId());if(session==null)throw ChatError.missing();var now=now();
            if(!java.util.Objects.equals(session.activeRunId(),run.runId()))return false;
            if(!session.runDeadlineAt().isAfter(now)) {
                boolean saved=statusCheckTimeout(run,now,text);return status.equals("TIMED_OUT")&&saved;
            }
            if(status.equals("TIMED_OUT"))sessions.expireRun(run.owner(),run.sessionId(),run.runId(),now);
            if(messages.finishOwned(run.owner(),run.sessionId(),run.messageId(),run.runId(),status,bounded(text),error,
                    usage==null?null:usage.inputTokens(),usage==null?null:usage.outputTokens(),now)!=1)return false;
            if(status.equals("COMPLETED")&&messages.saveSourcesOwned(run.owner(),run.sessionId(),run.messageId(),run.runId(),ChatSources.encode(sources))!=1)
                throw ChatError.storage();
            if(sessions.releaseRun(run.owner(),run.sessionId(),run.runId(),now)!=1)throw ChatError.storage();
            return true;
        }));
    }
    private boolean statusCheckTimeout(PreparedRun run,LocalDateTime now,String text) {
        if(messages.finishOwned(run.owner(),run.sessionId(),run.messageId(),run.runId(),"TIMED_OUT",bounded(text),"TIMEOUT",null,null,now)!=1)return false;
        if(sessions.releaseRun(run.owner(),run.sessionId(),run.runId(),now)!=1)throw ChatError.storage();
        return true;
    }
    private String bounded(String text) {
        if(text==null)return "";int count=text.codePointCount(0,text.length());
        return count<=p.getMaxOutputChars()?text:text.substring(0,text.offsetByCodePoints(0,p.getMaxOutputChars()));
    }
    public boolean markStreaming(PreparedRun run) {
        return messages.markStreaming(run.owner(),run.sessionId(),run.messageId(),run.runId(),now())==1;
    }
    public com.gba.merchantbao.agent.service.AgentModel.Call execute(PreparedRun run,String requestId,com.gba.merchantbao.agent.service.AgentModel.Listener downstream) {
        if(run.duplicate())throw new IllegalArgumentException("Duplicate run has no stream to execute");
        if(attempts==null)throw new IllegalStateException("Agent runtime unavailable");
        if(!admission.tryAcquire()) {
            finish(run,"FAILED","","DEPENDENCY_ERROR",null);
            throw new ChatError(503,com.gba.merchantbao.common.error.CommonErrorCode.DEPENDENCY_ERROR);
        }
        var holder=new java.util.concurrent.atomic.AtomicReference<ChatRunExecution>();
        var execution=new ChatRunExecution(this,run,requestId,p,downstream,()->{admission.release();active.remove(holder.get());},clock,metrics);
        holder.set(execution);
        active.add(execution);
        execution.bindDeadline(deadlines.schedule(()->execution.error(new java.util.concurrent.TimeoutException()),
                Math.max(0,Duration.between(clock.instant(),run.deadline()).toMillis()),java.util.concurrent.TimeUnit.MILLISECONDS));
        try {
            execution.bindPreparation(runtime.submit(()->{
                if(execution.terminal())return;
                try {
                    var prompt=run.context();
                    java.util.List<com.gba.merchantbao.knowledge.vo.KnowledgeSource> sources=java.util.List.of();
                    if(retrieval!=null&&retrieval.enabled()) {
                        var budget=new com.gba.merchantbao.knowledge.service.ImportDeadline(Duration.between(clock.instant(),run.deadline()),execution::terminal);
                        var question=((dev.langchain4j.data.message.UserMessage)prompt.getLast()).singleText();
                        var references=retrieval.retrieve(run.owner().merchantId(),question,budget);
                        if(execution.terminal())return;
                        budget.remaining();
                        var grounded=context.withEvidence(prompt,references);
                        if(grounded.sources().isEmpty()) {
                            execution.delta(com.gba.merchantbao.knowledge.service.KnowledgeRetrieval.NO_MATCH);
                            execution.complete(null);return;
                        }
                        prompt=grounded.messages();
                        sources=grounded.sources();
                    }
                    execution.generate(attempts,prompt,sources);
                }
                catch(RuntimeException failure){execution.error(failure);}
                finally {if(execution.terminal())active.remove(execution);}
            }));
        } catch(java.util.concurrent.RejectedExecutionException error) {execution.error(error);}
        return new com.gba.merchantbao.agent.service.AgentModel.Call(){
            public void cancel(){execution.cancel();active.remove(execution);}
            public void fail(){execution.error(new IllegalStateException("Stream resource unavailable"));active.remove(execution);}
        };
    }
    @jakarta.annotation.PreDestroy public void shutdown(){active.forEach(ChatRunExecution::cancel);runtime.shutdownNow();deadlines.shutdownNow();}
    public PreparedRun accept(Actor actor,long sessionId,String key,String content) {
        var o=owner(actor);
        if(key==null||!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                ||content==null||content.isBlank()||content.codePointCount(0,content.length())>p.getMaxInputChars())throw ChatError.invalid();
        return tx.execute(s->{
            var session=owned(o,sessionId,true);
            var original=messages.findUserByClientId(o,sessionId,key);
            if(original!=null) {
                if(!original.content().equals(content))throw new ChatError(409,com.gba.merchantbao.common.error.CommonErrorCode.CHAT_IDEMPOTENCY_CONFLICT);
                var a=messages.findAssistantByRun(o,sessionId,original.runId());if(a==null)throw ChatError.storage();
                return new PreparedRun(o,sessionId,a.runId(),original.id(),a.id(),a.status(),true,null,java.util.List.of());
            }
            if(session.status().equals("ARCHIVED"))throw new ChatError(409,com.gba.merchantbao.common.error.CommonErrorCode.CHAT_SESSION_ARCHIVED);
            if(session.activeRunId()!=null)throw ChatError.busy();
            if(!p.isEnabled())throw new ChatError(503,com.gba.merchantbao.common.error.CommonErrorCode.AGENT_DISABLED);
            java.util.List<dev.langchain4j.data.message.ChatMessage> prompt;
            try {prompt=context.assemble(p.getHistoryMessages()==0?java.util.List.of():messages.recentSuccessfulOwned(o,sessionId,p.getHistoryMessages()),content,retrieval!=null&&retrieval.enabled());}
            catch(IllegalArgumentException e){throw new ChatError(400,com.gba.merchantbao.common.error.CommonErrorCode.AGENT_CONTEXT_TOO_LARGE);}
            var now=now();var deadline=now.plus(p.getTotalTimeout());long run=ids.nextId(),user=ids.nextId(),assistant=ids.nextId();long seq=session.nextMessageSeq();
            if(sessions.acquireRun(o,sessionId,seq,run,deadline,now)!=1)throw ChatError.busy();
            var u=new ChatMessageRow(user,o.merchantId(),sessionId,seq,"USER",o.accountId(),key,content,"COMPLETED",run,null,null,null,null,null,null,now,now);
            var a=new ChatMessageRow(assistant,o.merchantId(),sessionId,seq+1,"ASSISTANT",null,null,"","STARTED",run,null,p.getPromptVersion(),p.getServiceLabel(),p.getModel(),null,null,now,null);
            if(messages.insertOwned(o,u)!=1||messages.insertOwned(o,a)!=1)throw ChatError.storage();
            return new PreparedRun(o,sessionId,run,user,assistant,"STARTED",false,deadline.toInstant(ZoneOffset.UTC),prompt);
        });
    }
    public ChatSessionRow archive(Actor actor,long sessionId) {
        var o=owner(actor);
        return tx.execute(s->{var row=owned(o,sessionId,true);if(row.status().equals("ARCHIVED"))return row;
            if(row.activeRunId()!=null)throw ChatError.busy();
            if(sessions.archiveOwned(o,sessionId,now())!=1)throw ChatError.busy();return owned(o,sessionId,false);});
    }
    public ChatPage<ChatSessionRow> list(Actor actor,String cursor,int size) {
        size(size); var o=owner(actor); LocalDateTime before=null;Long id=null;
        String value=ChatCursor.decode("s",cursor);
        if(value!=null)try{int split=value.lastIndexOf('|');before=LocalDateTime.parse(value.substring(0,split));id=positive(value.substring(split+1));}catch(RuntimeException e){throw ChatError.invalid();}
        var rows=sessions.pageOwned(o,before,id,size);String next=null;
        if(!rows.isEmpty()) {var last=rows.getLast();if(!sessions.pageOwned(o,last.updatedAt(),last.id(),1).isEmpty())next=ChatCursor.encode("s",last.updatedAt()+"|"+last.id());}
        return new ChatPage<>(rows,next,next!=null);
    }
    public ChatPage<ChatMessageRow> history(Actor actor,long sessionId,String cursor,int size) {
        size(size);var o=owner(actor);tx.execute(s->{owned(o,sessionId,true);return null;});long seq=0;
        String value=ChatCursor.decode("m"+sessionId,cursor);
        if(value!=null)seq=positive(value);
        var rows=messages.pageOwned(o,sessionId,seq,size);String next=null;
        if(!rows.isEmpty()){var last=rows.getLast();if(!messages.pageOwned(o,sessionId,last.messageSeq(),1).isEmpty())next=ChatCursor.encode("m"+sessionId,Long.toString(last.messageSeq()));}
        return new ChatPage<>(rows,next,next!=null);
    }
    private ChatSessionRow owned(ChatOwner o,long id,boolean lock) {
        var row=lock?sessions.lockOwned(o,id):sessions.findOwned(o,id);if(row==null)throw ChatError.missing();
        if(lock&&row.activeRunId()!=null&&row.runDeadlineAt()!=null&&!row.runDeadlineAt().isAfter(now())) {
            var assistant=messages.findAssistantByRun(o,id,row.activeRunId());
            if(assistant!=null&&messages.finishOwned(o,id,assistant.id(),assistant.runId(),"TIMED_OUT",bounded(assistant.content()),"TIMEOUT",null,null,now())==1)
                sessions.releaseRun(o,id,assistant.runId(),now());
            row=sessions.lockOwned(o,id);if(row==null)throw ChatError.missing();
        }
        return row;
    }
    private void size(int size){if(size<1||size>p.getMaxPageSize())throw ChatError.invalid();}
    public static long positive(String value){try{if(!value.matches("[1-9][0-9]{0,18}"))throw ChatError.invalid();return Long.parseLong(value);}catch(RuntimeException e){throw ChatError.invalid();}}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);}
}
