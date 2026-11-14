package com.gba.merchantbao.agent.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import dev.langchain4j.data.message.*;
import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static com.gba.merchantbao.agent.service.FunctionCallingFixtures.orderCall;

class AgentToolConversationTest {
    @FunctionalInterface interface Turn { AgentModel.Call run(List<ChatMessage> messages,boolean tools,AgentModel.Listener listener); }
    final List<ChatMessage> prompt=List.of(SystemMessage.from("system"),UserMessage.from("查 SO-1"));
    static class Result implements AgentModel.Listener {
        final StringBuilder text=new StringBuilder();
        final CompletableFuture<Result> finished=new CompletableFuture<>();
        final AtomicInteger terminals=new AtomicInteger();
        AgentModel.Usage usage;Throwable error;
        public void delta(String delta){text.append(delta);}
        public void complete(AgentModel.Usage usage){this.usage=usage;terminals.incrementAndGet();finished.complete(this);}
        public void error(Throwable error){this.error=error;terminals.incrementAndGet();finished.complete(this);}
        Result await() throws Exception{return finished.get(3,TimeUnit.SECONDS);}
    }
    static class Harness implements AutoCloseable {
        final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        final AgentProperties p=new AgentProperties();
        final AgentAttempts attempts;
        Harness(Turn turn){this(turn,Clock.systemUTC());}
        Harness(Turn turn,Clock clock) {
            p.setRetryBackoff(Duration.ofMillis(1));
            AgentModel model=new AgentModel(){
                public Call stream(List<ChatMessage> m,Listener l){return turn.run(m,false,l);}
                public Call stream(List<ChatMessage> m,boolean tools,Listener l){return turn.run(m,tools,l);}
            };
            attempts=new AgentAttempts(model,p,timer,clock);
        }
        AgentModel.Call start(List<ChatMessage> prompt,Instant deadline,Function<OrderLookupCall,String> lookup,Result result) {
            return attempts.stream(prompt,deadline,lookup,worker,result);
        }
        public void close(){timer.shutdownNow();worker.shutdownNow();}
    }
    @Test void selectsQueriesAndStreamsFinalAnswerInOrderAndAddsUsage() throws Exception {
        var events=new CopyOnWriteArrayList<String>();var reads=new AtomicInteger();var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{
            if(turns.incrementAndGet()==1) {assertThat(tools).isTrue();events.add("select");l.toolCall(orderCall(),new AgentModel.Usage(10L,2L));}
            else {
                assertThat(tools).isFalse();
                assertThat(messages.subList(0,2)).isEqualTo(prompt);
                assertThat(messages.get(2)).isEqualTo(orderCall().assistantMessage());
                assertThat(((ToolExecutionResultMessage)messages.getLast()).text()).isEqualTo("{\"status\":\"SHIPPED\"}");
                events.add("generate");l.delta("已发");l.delta("货");l.complete(new AgentModel.Usage(20L,3L));
            }
            return ()->{};
        })) {
            var result=new Result();
            h.start(prompt,Instant.now().plusSeconds(3),call->{reads.incrementAndGet();events.add("query");return "{\"status\":\"SHIPPED\"}";},result);
            result.await();
            assertThat(result.error).isNull();assertThat(result.text.toString()).isEqualTo("已发货");
            assertThat(events).containsExactly("select","query","generate");
            assertThat(reads.get()).isOne();assertThat(result.terminals.get()).isOne();
            assertThat(result.usage).isEqualTo(new AgentModel.Usage(30L,5L));
        }
    }
    @Test void secondToolRequestFailsWithoutASecondRead() throws Exception {
        var reads=new AtomicInteger();var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{turns.incrementAndGet();l.toolCall(orderCall(),null);return ()->{};})) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->{reads.incrementAndGet();return "{}";},result);
            assertThat(result.await().error).isInstanceOf(AgentModel.ProtocolException.class);
            assertThat(reads.get()).isOne();assertThat(turns.get()).isEqualTo(2);assertThat(result.text).isEmpty();
        }
    }
    @Test void finalModelRetryReusesTheSameToolResultWithoutReadingAgain() throws Exception {
        var reads=new AtomicInteger();var turns=new AtomicInteger();var finalPrompts=new CopyOnWriteArrayList<List<ChatMessage>>();
        try(var h=new Harness((messages,tools,l)->{
            int turn=turns.incrementAndGet();
            if(tools)l.toolCall(orderCall(),null);
            else {finalPrompts.add(messages);if(turn==2)l.error(new HttpException(503,"private"));else{l.delta("ok");l.complete(null);}}
            return ()->{};
        })) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->{reads.incrementAndGet();return "{}";},result);
            assertThat(result.await().error).isNull();assertThat(result.text.toString()).isEqualTo("ok");
            assertThat(reads.get()).isOne();assertThat(turns.get()).isEqualTo(3);
            assertThat(finalPrompts).hasSize(2);assertThat(finalPrompts.getFirst()).isEqualTo(finalPrompts.getLast());
        }
    }
    @Test void retryAllowanceIsSharedBySelectionAndFinalGeneration() throws Exception {
        var reads=new AtomicInteger();var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{
            int turn=turns.incrementAndGet();
            if(turn==1||turn==3)l.error(new HttpException(503,"private"));else l.toolCall(orderCall(),null);
            return ()->{};
        })) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->{reads.incrementAndGet();return "{}";},result);
            assertThat(result.await().error).isInstanceOf(HttpException.class);
            assertThat(reads.get()).isOne();assertThat(turns.get()).isEqualTo(3);
        }
    }
    @Test void lookupFailureNeverRetriesOrStartsFinalGeneration() throws Exception {
        var turns=new AtomicInteger();var reads=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{turns.incrementAndGet();l.toolCall(orderCall(),null);return ()->{};})) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->{reads.incrementAndGet();throw new IllegalStateException("storage unavailable");},result);
            assertThat(result.await().error).isInstanceOf(IllegalStateException.class);
            assertThat(turns.get()).isOne();assertThat(reads.get()).isOne();assertThat(result.text).isEmpty();
        }
    }
    @Test void oversizedResultFailsWithoutFinalGeneration() throws Exception {
        var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{turns.incrementAndGet();l.toolCall(orderCall(),null);return ()->{};})) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->"A".repeat(9000),result);
            assertThat(result.await().error).isInstanceOf(AgentModel.ProtocolException.class);
            assertThat(turns.get()).isOne();
        }
    }
    @Test void duplicateAndLateSelectionCallbacksCannotReadTwiceOrFinishTheFinalRound() throws Exception {
        var original=new AtomicReference<AgentModel.Listener>();var last=new AtomicReference<AgentModel.Listener>();
        var finalStarted=new CountDownLatch(1);var reads=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{
            if(tools){original.set(l);l.toolCall(orderCall(),null);l.toolCall(orderCall(),null);}
            else{last.set(l);finalStarted.countDown();}
            return ()->{};
        })) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->{reads.incrementAndGet();return "{}";},result);
            assertThat(finalStarted.await(2,TimeUnit.SECONDS)).isTrue();
            original.get().delta("late");original.get().complete(null);original.get().error(new java.io.IOException());
            assertThat(result.finished).isNotDone();last.get().delta("ok");last.get().complete(null);
            assertThat(result.await().text.toString()).isEqualTo("ok");assertThat(reads.get()).isOne();
        }
    }
    @Test void emittedFinalBodyPreventsRetry() throws Exception {
        var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{
            turns.incrementAndGet();if(tools)l.toolCall(orderCall(),null);
            else{l.delta("partial");l.error(new java.io.IOException());}
            return ()->{};
        })) {
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->"{}",result);
            assertThat(result.await().error).isInstanceOf(java.io.IOException.class);
            assertThat(result.text.toString()).isEqualTo("partial");assertThat(turns.get()).isEqualTo(2);
        }
    }
    static class MutableClock extends Clock {
        volatile Instant now=Instant.now();
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void lookupAndFinalCallbacksMustRespectTheOriginalDeadline() throws Exception {
        for(boolean expireInLookup:List.of(true,false)) {
            var clock=new MutableClock();var deadline=clock.instant().plusSeconds(10);var turns=new AtomicInteger();
            try(var h=new Harness((messages,tools,l)->{
                turns.incrementAndGet();
                if(tools)l.toolCall(orderCall(),null);
                else{clock.now=deadline;l.delta("late");l.complete(null);}
                return ()->{};
            },clock)) {
                var result=new Result();h.start(prompt,deadline,call->{if(expireInLookup)clock.now=deadline;return "{}";},result);
                assertThat(result.await().error).isInstanceOf(TimeoutException.class);
                assertThat(result.text).isEmpty();assertThat(turns.get()).isEqualTo(expireInLookup?1:2);
                assertThat(result.terminals.get()).isOne();
            }
        }
    }
    @Test void cancelDuringLookupInterruptsWorkAndSuppressesItsLateResult() throws Exception {
        var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);var done=new CountDownLatch(1);
        var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{turns.incrementAndGet();l.toolCall(orderCall(),null);return ()->{};})) {
            var result=new Result();
            var call=h.start(prompt,Instant.now().plusSeconds(3),request->{
                entered.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();}
                finally{done.countDown();}return "{}";
            },result);
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();call.cancel();
            assertThat(interrupted.await(2,TimeUnit.SECONDS)).isTrue();assertThat(done.await(2,TimeUnit.SECONDS)).isTrue();
            h.worker.submit(()->{}).get(2,TimeUnit.SECONDS);
            assertThat(turns.get()).isOne();assertThat(result.finished).isNotDone();
        }
    }
    @Test void firstTokenTimeoutIncludesLookupAndInterruptsIt() throws Exception {
        var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);var turns=new AtomicInteger();
        try(var h=new Harness((messages,tools,l)->{turns.incrementAndGet();l.toolCall(orderCall(),null);return ()->{};})) {
            h.p.setFirstTokenTimeout(Duration.ofMillis(200));
            var result=new Result();h.start(prompt,Instant.now().plusSeconds(3),call->{
                entered.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();}return "{}";
            },result);
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(result.await().error).isInstanceOf(TimeoutException.class);
            assertThat(interrupted.await(2,TimeUnit.SECONDS)).isTrue();assertThat(turns.get()).isOne();
        }
    }
    @Test void totalTimeoutCancelsFinalRoundEvenAfterBody() throws Exception {
        var cancelled=new CountDownLatch(1);
        try(var h=new Harness((messages,tools,l)->{
            if(tools){l.toolCall(orderCall(),null);return ()->{};}
            l.delta("partial");return cancelled::countDown;
        })) {
            var result=new Result();h.start(prompt,Instant.now().plusMillis(250),call->"{}",result);
            assertThat(result.await().error).isInstanceOf(TimeoutException.class);
            assertThat(result.text.toString()).isEqualTo("partial");
            assertThat(cancelled.await(2,TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void lateLookupFutureRegistrationCannotInterruptASuccessfulCommit() throws Exception {
        var committing=new CountDownLatch(1);var release=new CountDownLatch(1);
        var timer=Executors.newSingleThreadScheduledExecutor();
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>()) {
            @Override public Future<?> submit(Runnable action) {
                var future=super.submit(action);
                try{if(!committing.await(2,TimeUnit.SECONDS))throw new AssertionError("No final completion");}
                catch(InterruptedException error){throw new AssertionError(error);}
                return future;
            }
        };
        AgentModel model=new AgentModel() {
            public Call stream(List<ChatMessage> m,Listener l){return stream(m,false,l);}
            public Call stream(List<ChatMessage> m,boolean tools,Listener l) {
                if(tools)l.toolCall(orderCall(),null);else l.complete(null);
                return ()->{};
            }
        };
        var p=new AgentProperties();
        var result=new Result() {
            @Override public void complete(AgentModel.Usage usage) {
                committing.countDown();
                try{release.await(2,TimeUnit.SECONDS);super.complete(usage);}
                catch(InterruptedException interrupted){error(new IllegalStateException("commit interrupted"));}
            }
        };
        try {
            new AgentAttempts(model,p,timer,Clock.systemUTC()).stream(prompt,Instant.now().plusSeconds(3),call->"{}",worker,result);
            release.countDown();
            assertThat(result.await().error).isNull();
        } finally{release.countDown();worker.shutdownNow();timer.shutdownNow();}
    }

    @Test void finalCompletionDoesNotInterruptItsOwnSynchronousWorker() throws Exception {
        try(var h=new Harness((messages,tools,l)->{
            if(tools)l.toolCall(orderCall(),null);else l.complete(null);return ()->{};
        })) {
            var result=new Result(){
                @Override public void complete(AgentModel.Usage usage){
                    if(Thread.currentThread().isInterrupted())error(new IllegalStateException("self-interrupted"));
                    else super.complete(usage);
                }
            };
            h.start(prompt,Instant.now().plusSeconds(3),call->"{}",result);
            assertThat(result.await().error).isNull();
        }
    }
}
