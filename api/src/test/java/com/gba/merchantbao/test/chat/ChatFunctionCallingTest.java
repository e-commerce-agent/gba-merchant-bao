package com.gba.merchantbao.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.*;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.chat.entity.*;
import com.gba.merchantbao.chat.mapper.*;
import com.gba.merchantbao.knowledge.service.KnowledgeRetrieval;
import com.gba.merchantbao.knowledge.vo.KnowledgeSource;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.order.entity.OrderRow;
import com.gba.merchantbao.order.mapper.*;
import com.gba.merchantbao.order.service.OrderQueryService;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.gba.merchantbao.agent.service.FunctionCallingFixtures.orderCall;

class ChatFunctionCallingTest {
    static final String QUESTION="  查询订单 SO-1\n保持原问题 😀  ";
    static final KnowledgeSource SOURCE=new KnowledgeSource(new ApiId(90),new ApiId(91),"订单政策",1,"订单付款后发货");
    static class Outcome implements ChatRunListener {
        final StringBuilder text=new StringBuilder();
        final CompletableFuture<Outcome> done=new CompletableFuture<>();
        String error;List<KnowledgeSource> sources;
        public void delta(String text){this.text.append(text);}
        public void complete(AgentModel.Usage usage,List<KnowledgeSource> sources){this.sources=sources;done.complete(this);}
        public void error(Throwable error){this.error=error.getMessage();done.complete(this);}
        Outcome await() throws Exception{return done.get(3,TimeUnit.SECONDS);}
    }
    static class Harness implements AutoCloseable {
        final AgentProperties p=new AgentProperties();
        final ChatSessionMapper sessions=mock(ChatSessionMapper.class);
        final ChatMessageMapper messages=mock(ChatMessageMapper.class);
        final OrderQueryMapper orders=mock(OrderQueryMapper.class);
        final OrderItemQueryMapper items=mock(OrderItemQueryMapper.class);
        final KnowledgeRetrieval retrieval=mock(KnowledgeRetrieval.class);
        final AgentModel model=mock(AgentModel.class);
        final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        final ChatService service;
        final AtomicReference<String> savedStatus=new AtomicReference<>();
        final AtomicReference<String> savedText=new AtomicReference<>();
        final AtomicReference<List<KnowledgeSource>> savedSources=new AtomicReference<>();
        final AtomicInteger turns=new AtomicInteger();
        final List<List<ChatMessage>> prompts=new CopyOnWriteArrayList<>();
        final List<Boolean> enabledTools=new CopyOnWriteArrayList<>();
        Actor actor=new Actor(new AuthPrincipal(10,1,AccountRole.CONSUMER),"trusted-session");
        Harness() {
            p.setEnabled(true);p.setWorkers(2);p.setTotalTimeout(Duration.ofSeconds(3));
            var tx=mock(PlatformTransactionManager.class);
            when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            var ids=new AtomicLong(100);
            service=spy(new ChatService(sessions,messages,ids::incrementAndGet,Clock.systemUTC(),p,tx,
                    new AgentAttempts(model,p,timer,Clock.systemUTC()),model));
            service.orderLookup(new AgentOrderLookup(new OrderQueryService(orders,items),new ObjectMapper().findAndRegisterModules()));
            service.retrieval(retrieval);
            when(retrieval.enabled()).thenReturn(true);
            when(retrieval.retrieve(eq(1L),eq(QUESTION),any())).thenReturn(List.of());
            doReturn(true).when(service).markStreaming(any());
            doAnswer(i->{
                savedStatus.set(i.getArgument(1));savedText.set(i.getArgument(2));savedSources.set(List.copyOf(i.getArgument(5)));
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                return true;
            }).when(service).finish(any(),anyString(),anyString(),nullable(String.class),nullable(AgentModel.Usage.class),anyList());
            var now=Instant.now();
            var row=new OrderRow(50,1,"SO-1",10,"SHIPPED","PAID",new BigDecimal("79.00"),new BigDecimal("79.00"),
                    "CNY",now,now,now,0);
            when(orders.findOwnedByOrderNo(1,"SO-1",10)).thenReturn(row);
            when(orders.findByOrderNo(1,"SO-1")).thenReturn(row);
            when(model.stream(anyList(),anyBoolean(),any())).thenAnswer(i->{
                List<ChatMessage> prompt=List.copyOf(i.getArgument(0));
                boolean tools=i.getArgument(1);AgentModel.Listener l=i.getArgument(2);
                prompts.add(prompt);enabledTools.add(tools);turns.incrementAndGet();
                if(tools)l.toolCall(orderCall(),null);
                else {
                    assertThat(prompt.get(prompt.size()-2)).isEqualTo(orderCall().assistantMessage());
                    assertThat(((ToolExecutionResultMessage)prompt.getLast()).text()).contains("SHIPPED","PAID","79");
                    l.delta("订单已发货");l.complete(null);
                }
                return (AgentModel.Call)()->{};
            });
        }
        PreparedRun run() {
            var prompt=new AgentContext(p).assemble(List.of(),QUESTION,true);
            return new PreparedRun(ChatService.owner(actor),31,41,51,61,"STARTED",false,Instant.now().plusSeconds(3),prompt,actor);
        }
        Outcome execute(PreparedRun run) throws Exception {
            var result=new Outcome();service.execute(run,"tool-runtime",result);return result.await();
        }
        public void close(){service.shutdown();timer.shutdownNow();}
    }
    @Test void noRagMatchUsesOriginalQuestionAndRealOrderServiceWithTrustedConsumerActor() throws Exception {
        try(var h=new Harness()) {
            var result=h.execute(h.run());
            assertThat(result.error).isNull();assertThat(result.text.toString()).isEqualTo("订单已发货");
            assertThat(result.sources).isEmpty();assertThat(h.savedSources.get()).isEmpty();
            assertThat(h.savedStatus.get()).isEqualTo("COMPLETED");
            assertThat(h.savedText.get()).isEqualTo("订单已发货");
            assertThat(h.prompts.getFirst().getLast()).isEqualTo(UserMessage.from(QUESTION));
            assertThat(h.prompts.getFirst().stream().filter(m->m.equals(UserMessage.from(QUESTION)))).hasSize(1);
            assertThat(h.enabledTools).containsExactly(true,false);
            verify(h.orders).findOwnedByOrderNo(1,"SO-1",10);
            verify(h.orders,never()).findByOrderNo(anyLong(),anyString());verify(h.items).findByOrder(1,50);
        }
    }
    @Test void staffUsesMerchantScopedOrderQuery() throws Exception {
        try(var h=new Harness()) {
            h.actor=new Actor(new AuthPrincipal(20,1,AccountRole.MERCHANT_STAFF),"staff-session");
            assertThat(h.execute(h.run()).error).isNull();
            verify(h.orders).findByOrderNo(1,"SO-1");
            verify(h.orders,never()).findOwnedByOrderNo(anyLong(),anyString(),anyLong());
        }
    }
    @Test void evidenceAndOrderToolCoexistAndKeepTheSameSources() throws Exception {
        try(var h=new Harness()) {
            when(h.retrieval.retrieve(anyLong(),anyString(),any())).thenReturn(List.of(SOURCE));
            var result=h.execute(h.run());
            assertThat(result.error).isNull();assertThat(result.sources).containsExactly(SOURCE);
            assertThat(h.savedSources.get()).containsExactly(SOURCE);
            assertThat(((UserMessage)h.prompts.getFirst().get(1)).singleText()).contains(SOURCE.excerpt());
            assertThat(h.prompts.getLast().subList(0,h.prompts.getFirst().size())).isEqualTo(h.prompts.getFirst());
            verify(h.retrieval).retrieve(anyLong(),eq(QUESTION),any());
        }
    }
    @Test void referencesThatDoNotFitStillAllowAnOrderLookup() throws Exception {
        try(var h=new Harness()) {
            when(h.retrieval.retrieve(anyLong(),anyString(),any())).thenReturn(List.of(
                    new KnowledgeSource(new ApiId(90),new ApiId(91),"oversized",1,"A".repeat(10000))));
            assertThat(h.execute(h.run()).sources).isEmpty();
            assertThat(h.turns.get()).isEqualTo(2);
            assertThat(h.prompts.getFirst().getLast()).isEqualTo(UserMessage.from(QUESTION));
        }
    }
    @Test void retrievalFailureNeverCallsModelOrOrderService() throws Exception {
        try(var h=new Harness()) {
            when(h.retrieval.retrieve(anyLong(),anyString(),any())).thenThrow(new IllegalStateException("private retrieval fault"));
            var result=h.execute(h.run());
            assertThat(result.error).isEqualTo("DEPENDENCY_ERROR");assertThat(result.text).isEmpty();
            assertThat(h.savedStatus.get()).isEqualTo("FAILED");assertThat(h.savedSources.get()).isEmpty();
            verifyNoInteractions(h.model,h.orders,h.items);
        }
    }
    @Test void inaccessibleOrderOrStorageFailureEndsWithoutFinalGeneration() throws Exception {
        for(boolean storageFailure:List.of(false,true)) {
            try(var h=new Harness()) {
                if(storageFailure)when(h.orders.findOwnedByOrderNo(1,"SO-1",10))
                        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database details"));
                else when(h.orders.findOwnedByOrderNo(1,"SO-1",10)).thenReturn(null);
                var result=h.execute(h.run());
                assertThat(result.error).isEqualTo("DEPENDENCY_ERROR");assertThat(result.text).isEmpty();
                assertThat(h.savedStatus.get()).isEqualTo("FAILED");assertThat(h.turns.get()).isOne();
                verifyNoInteractions(h.items);
            }
        }
    }
    @Test void acceptancePreservesTheExactAuthenticationActor() {
        try(var h=new Harness()) {
            var now=LocalDateTime.now(ZoneOffset.UTC);
            when(h.sessions.lockOwned(any(),eq(31L))).thenReturn(
                    new ChatSessionRow(31,1,10,"CONSUMER","session","ACTIVE",now,now,null,null,1,null,null));
            when(h.sessions.acquireRun(any(),anyLong(),anyLong(),anyLong(),any(),any())).thenReturn(1);
            when(h.messages.insertOwned(any(),any())).thenReturn(1);
            var run=h.service.accept(h.actor,31,UUID.randomUUID().toString(),QUESTION);
            assertThat(run.actor()).isSameAs(h.actor);assertThat(run.owner()).isEqualTo(ChatService.owner(h.actor));
        }
    }
    @Test void inconsistentActorAndRunOwnerCannotReadAnOrder() throws Exception {
        try(var h=new Harness()) {
            var accepted=h.run();
            var forged=new PreparedRun(accepted.owner(),31,41,51,61,"STARTED",false,accepted.deadline(),accepted.context(),
                    new Actor(new AuthPrincipal(999,2,AccountRole.CONSUMER),"wrong"));
            assertThat(h.execute(forged).error).isEqualTo("DEPENDENCY_ERROR");
            verifyNoInteractions(h.orders,h.items);assertThat(h.turns.get()).isOne();
        }
    }
    @Test void cancellationDuringOrderReadPersistsOneCancelledTerminalAndDoesNotGenerate() throws Exception {
        try(var h=new Harness()) {
            var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);
            when(h.orders.findOwnedByOrderNo(1,"SO-1",10)).thenAnswer(i->{
                entered.countDown();
                try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();}
                return null;
            });
            var result=new Outcome();var call=h.service.execute(h.run(),"cancel-tool",result);
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();call.cancel();
            assertThat(result.await().error).isEqualTo("CANCELLED");
            assertThat(interrupted.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(h.savedStatus.get()).isEqualTo("CANCELLED");assertThat(h.turns.get()).isOne();
            verify(h.service).finish(any(),eq("CANCELLED"),eq(""),eq("CANCELLED"),isNull(),eq(List.of()));
        }
    }
}
