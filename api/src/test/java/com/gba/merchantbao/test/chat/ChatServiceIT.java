package com.gba.merchantbao.test.chat;

import com.gba.merchantbao.test.support.DockerRequired;

import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.chat.service.ChatService;
import com.gba.merchantbao.agent.service.AgentModel;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.data.redis.host=127.0.0.1","spring.data.redis.port=1",
        "merchant.agent.enabled=true","merchant.agent.base-url=http://127.0.0.1:9/v1","merchant.agent.api-key=test-only-key","merchant.agent.model=stub-model"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
class ChatServiceIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("chat_service").withUsername("test").withPassword("test").withReuse(false);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired ChatService service; @Autowired JdbcTemplate jdbc;
    @Autowired com.gba.merchantbao.chat.mapper.ChatSessionMapper sessionMapper;
    @Autowired com.gba.merchantbao.common.id.IdGenerator ids;
    @Autowired com.gba.merchantbao.agent.config.AgentProperties config;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean AgentModel model;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.gba.merchantbao.chat.mapper.ChatMessageMapper messages;
    Actor actor(long account,String merchant,AccountRole role) {
        long tenant=jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code=?",Long.class,merchant);
        return new Actor(new AuthPrincipal(account,tenant,role),"test-only-session");
    }
    @Test void consumersAndStaffCanOnlyManageTheirOwnSessionsAndArchiveIsIdempotent() {
        for(var role:AccountRole.values()) {
            var owner=actor(100,"M_3C_DEMO",role);
            var session=service.create(owner,"  我的对话  ");
            assertThat(session.title()).isEqualTo("我的对话");
            assertThat(service.detail(owner,session.id()).id()).isEqualTo(session.id());
            jdbc.update("UPDATE chat_session SET active_run_id=991,run_deadline_at=UTC_TIMESTAMP(3)+INTERVAL 30 SECOND WHERE merchant_id=? AND id=?",owner.principal().merchantId(),session.id());
            assertThatThrownBy(()->service.archive(owner,session.id())).hasMessage("chat session busy");
            jdbc.update("UPDATE chat_session SET active_run_id=NULL,run_deadline_at=NULL WHERE merchant_id=? AND id=?",owner.principal().merchantId(),session.id());
            assertThat(service.list(owner,null,20).items()).extracting(s->s.id()).contains(session.id());
            for(var wrong:new Actor[]{actor(101,"M_3C_DEMO",role),actor(100,"M_ISOLATION_TEST",role),
                    actor(100,"M_3C_DEMO",role==AccountRole.CONSUMER?AccountRole.MERCHANT_STAFF:AccountRole.CONSUMER)}) {
                assertThatThrownBy(()->service.detail(wrong,session.id())).hasMessage("resource not found");
                assertThatThrownBy(()->service.archive(wrong,session.id())).hasMessage("resource not found");
                assertThatThrownBy(()->service.history(wrong,session.id(),null,20)).hasMessage("resource not found");
            }
            var archived=service.archive(owner,session.id());
            assertThat(archived.status()).isEqualTo("ARCHIVED");
            assertThat(service.archive(owner,session.id()).archivedAt()).isEqualTo(archived.archivedAt());
            assertThat(service.history(owner,session.id(),null,20).items()).isEmpty();
        }
    }
    @Test void concurrentMessagesSerializeInTheDatabaseAndIdempotencyPrecedesBusy() throws Exception {
        var owner=actor(200,"M_3C_DEMO",AccountRole.CONSUMER);long id=service.create(owner,null).id();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Object>>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->{gate.await();try{return service.accept(owner,id,java.util.UUID.randomUUID().toString()," hello ");}catch(RuntimeException e){return e;}}));
            gate.countDown();var results=new java.util.ArrayList<Object>();for(var f:futures)results.add(f.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().filter(x->x instanceof com.gba.merchantbao.chat.service.PreparedRun).count()).isOne();
            assertThat(results.stream().filter(x->x instanceof RuntimeException).map(x->((RuntimeException)x).getMessage()).toList()).containsExactly("chat session busy");
            var rows=service.history(owner,id,null,20).items();assertThat(rows).hasSize(2);
            assertThat(rows).extracting(r->r.messageSeq()).containsExactly(1L,2L);
            var retry=service.accept(owner,id,rows.getFirst().clientMessageId()," hello ");
            assertThat(retry.duplicate()).isTrue();assertThat(retry.runId()).isEqualTo(rows.getFirst().runId());
            assertThatThrownBy(()->service.accept(owner,id,rows.getFirst().clientMessageId(),"hello")).hasMessage("chat idempotency conflict");
            assertThatThrownBy(()->service.archive(owner,id)).hasMessage("chat session busy");
            assertThat(service.accept(owner,service.create(owner,null).id(),java.util.UUID.randomUUID().toString(),"independent").duplicate()).isFalse();
        } finally {pool.shutdownNow();}
    }
    @Test void onlyOneTerminalCommitsAndLateCallbacksCannotOverwriteTheNextRun() throws Exception {
        var owner=actor(301,"M_3C_DEMO",AccountRole.CONSUMER);long id=service.create(owner,null).id();
        var run=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"question");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->service.finish(run,"COMPLETED","answer",null,new com.gba.merchantbao.agent.service.AgentModel.Usage(10L,2L)));
            var b=pool.submit(()->service.finish(run,"CANCELLED","partial","CANCELLED",null));
            assertThat(java.util.List.of(a.get(),b.get())).containsExactlyInAnyOrder(true,false);
            assertThat(service.detail(owner,id).activeRunId()).isNull();
            var saved=service.history(owner,id,null,20).items().getLast();
            assertThat(saved.status()).isIn("COMPLETED","CANCELLED");assertThat(saved.completedAt()).isNotNull();
            var next=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"next");
            assertThat(service.finish(run,"COMPLETED","late",null,null)).isFalse();
            assertThat(service.detail(owner,id).activeRunId()).isEqualTo(next.runId());
            service.finish(next,"FAILED","x".repeat(20000),"DEPENDENCY_ERROR",null);
            assertThat(service.history(owner,id,null,20).items().getLast().content()).hasSize(16000);
        } finally {pool.shutdownNow();}
    }
    @Test void aStorageFailureCannotBeReportedAsACommittedSuccess() {
        var owner=actor(302,"M_3C_DEMO",AccountRole.CONSUMER);long id=service.create(owner,null).id();
        var run=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"question");
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("injected"))
            .when(messages).finishOwned(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(id),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any());
        try { assertThatThrownBy(()->service.finish(run,"COMPLETED","answer",null,null)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { org.mockito.Mockito.reset(messages); }
        assertThat(service.history(owner,id,null,20).items().getLast().status()).isEqualTo("STARTED");
        assertThat(service.detail(owner,id).activeRunId()).isEqualTo(run.runId());
    }
    @Test void anotherExecutionContextRecoversAnAbandonedRunAndOldCallbacksCannotReleaseItsSuccessor() throws Exception {
        var owner=actor(400,"M_3C_DEMO",AccountRole.MERCHANT_STAFF);long id=service.create(owner,null).id();
        var first=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"first");
        var other=new ChatService(sessionMapper,messages,ids,java.time.Clock.fixed(first.deadline().plusSeconds(1),java.time.ZoneOffset.UTC),config,manager);
        assertThatThrownBy(()->service.accept(owner,id,java.util.UUID.randomUUID().toString(),"busy")).hasMessage("chat session busy");
        assertThat(other.detail(owner,id).activeRunId()).isNull();
        var history=other.history(owner,id,null,20).items();assertThat(history.getLast().status()).isEqualTo("TIMED_OUT");
        assertThat(history.getFirst().content()).isEqualTo("first");
        var next=other.accept(owner,id,java.util.UUID.randomUUID().toString(),"second");
        assertThat(service.finish(first,"COMPLETED","late",null,null)).isFalse();
        assertThat(other.detail(owner,id).activeRunId()).isEqualTo(next.runId());
        assertThat(other.accept(owner,id,history.getFirst().clientMessageId(),"first").status()).isEqualTo("TIMED_OUT");
    }
    @Test void modelStartsAfterMessagesCommitAndCompletionIsSentOnlyAfterTerminalCommit() throws Exception {
        var owner=actor(501,"M_3C_DEMO",AccountRole.CONSUMER);long id=service.create(owner,null).id();
        var run=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"persist first");
        var done=new java.util.concurrent.CountDownLatch(1);var committed=new java.util.concurrent.atomic.AtomicBoolean();
        when(model.stream(anyList(),eq(true),any())).thenAnswer(invocation->{
            var before=service.history(owner,id,null,20).items();
            committed.set(before.size()==2&&before.getFirst().status().equals("COMPLETED")&&before.getLast().status().equals("STARTED"));
            AgentModel.Listener callback=invocation.getArgument(2);callback.delta("saved answer");callback.complete(new AgentModel.Usage(2L,2L));
            return (AgentModel.Call)()->{};
        });
        var call=service.execute(run,"req-success",new AgentModel.Listener(){public void delta(String d){}public void complete(AgentModel.Usage u){
            assertThat(service.history(owner,id,null,20).items().getLast().status()).isEqualTo("COMPLETED");done.countDown();
        }public void error(Throwable e){done.countDown();}});
        assertThat(done.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(committed).isTrue();
        var history=service.history(owner,id,null,20).items();assertThat(history.getLast().content()).isEqualTo("saved answer");
        assertThat(history.getLast().inputTokens()).isEqualTo(2);assertThat(service.detail(owner,id).activeRunId()).isNull();
        call.cancel();
    }
    @Test void abortCancelsTheUpstreamAndPersistsCancelledState() throws Exception {
        var owner=actor(502,"M_3C_DEMO",AccountRole.CONSUMER);long id=service.create(owner,null).id();
        var run=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"stop me");
        var started=new java.util.concurrent.CountDownLatch(1);var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        when(model.stream(anyList(),eq(true),any())).thenAnswer(invocation->{started.countDown();return (AgentModel.Call)()->cancelled.set(true);});
        var call=service.execute(run,"req-cancel",new AgentModel.Listener(){public void delta(String d){}public void complete(AgentModel.Usage u){}public void error(Throwable e){}});
        assertThat(started.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();call.cancel();
        assertThat(cancelled).isTrue();assertThat(service.history(owner,id,null,20).items().getLast().status()).isEqualTo("CANCELLED");
        assertThat(service.detail(owner,id).activeRunId()).isNull();
    }
    @Test void firstTokenTimeoutReleasesTheRunAndDropsLateCallbacks() throws Exception {
        var owner=actor(503,"M_3C_DEMO",AccountRole.CONSUMER);long id=service.create(owner,null).id();
        var run=service.accept(owner,id,java.util.UUID.randomUUID().toString(),"timeout question");
        var callback=new java.util.concurrent.atomic.AtomicReference<AgentModel.Listener>();var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var p=new com.gba.merchantbao.agent.config.AgentProperties();p.setFirstTokenTimeout(java.time.Duration.ofMillis(30));
        var timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try {
            AgentModel stalled=(context,l)->{callback.set(l);return ()->cancelled.set(true);};
            var instance=new ChatService(sessionMapper,messages,ids,java.time.Clock.systemUTC(),p,manager,
                new com.gba.merchantbao.agent.service.AgentAttempts(stalled,p,timer,java.time.Clock.systemUTC()),stalled);
            var done=new java.util.concurrent.CompletableFuture<String>();
            instance.execute(run,"req-timeout",new AgentModel.Listener(){public void delta(String d){done.complete("late delta");}public void complete(AgentModel.Usage u){done.complete("bad completion");}public void error(Throwable e){done.complete(e.getMessage());}});
            assertThat(done.get(3,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("TIMEOUT");assertThat(cancelled).isTrue();
            callback.get().delta("late");callback.get().complete(null);
            assertThat(service.history(owner,id,null,20).items().getLast().status()).isEqualTo("TIMED_OUT");
            assertThat(service.detail(owner,id).activeRunId()).isNull();instance.shutdown();
        } finally {timer.shutdownNow();}
    }
    @Test void totalDeadlinePersistsPartialTextAndCancelsAStreamThatAlreadyProducedAToken() throws Exception {
        var owner=actor(504,"M_3C_DEMO",AccountRole.CONSUMER);
        var p=new com.gba.merchantbao.agent.config.AgentProperties();p.setEnabled(true);p.setModel("deadline-stub");
        p.setFirstTokenTimeout(java.time.Duration.ofSeconds(1));p.setTotalTimeout(java.time.Duration.ofMillis(500));
        var timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();var cancelled=new java.util.concurrent.CountDownLatch(1);
        AgentModel stalled=(context,l)->{l.delta("partial before timeout");return cancelled::countDown;};
        var instance=new ChatService(sessionMapper,messages,ids,java.time.Clock.systemUTC(),p,manager,
            new com.gba.merchantbao.agent.service.AgentAttempts(stalled,p,timer,java.time.Clock.systemUTC()),stalled);
        try {
            long id=instance.create(owner,null).id();var run=instance.accept(owner,id,java.util.UUID.randomUUID().toString(),"total deadline");
            var end=new java.util.concurrent.CompletableFuture<String>();
            instance.execute(run,"req-total-timeout",new AgentModel.Listener(){public void delta(String d){}public void complete(AgentModel.Usage u){end.complete("wrong done");}public void error(Throwable e){end.complete(e.getMessage());}});
            assertThat(end.get(3,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("TIMEOUT");
            assertThat(cancelled.await(1,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var message=instance.history(owner,id,null,20).items().getLast();
            assertThat(message.status()).isEqualTo("TIMED_OUT");assertThat(message.content()).isEqualTo("partial before timeout");
            assertThat(instance.detail(owner,id).activeRunId()).isNull();
        } finally {instance.shutdown();timer.shutdownNow();}
    }
}
