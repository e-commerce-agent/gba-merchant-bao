package com.gba.merchantbao.test.agent;

import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.*;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class AgentAttemptsTest {
    @Test void onlyTransientFailuresBeforeBodyRetryWithinOriginalDeadline() throws Exception {
        for(Throwable error:List.of(new java.io.IOException("network"),new HttpException(429,"rate"),new HttpException(503,"upstream"),new HttpException(401,"secret"),new HttpException(400,"input"))) {
            var count=new AtomicInteger();var scheduler=Executors.newSingleThreadScheduledExecutor();
            try {
                AgentModel model=(messages,l)->{if(count.incrementAndGet()==1)l.error(error);else{l.delta("ok");l.complete(null);}return ()->{};};
                var p=new AgentProperties();p.setRetryBackoff(Duration.ofMillis(1));
                var done=new CompletableFuture<String>();
                new AgentAttempts(model,p,scheduler,Clock.systemUTC()).stream(List.of(UserMessage.from("hi")),Instant.now().plusSeconds(2),listener(done));
                boolean retry=!(error instanceof HttpException h)||h.statusCode()==429||h.statusCode()>=500;
                assertThat(done.get(3,TimeUnit.SECONDS)).isEqualTo(retry?"done":"error");assertThat(count.get()).isEqualTo(retry?2:1);
            } finally {scheduler.shutdownNow();}
        }
    }
    @Test void emittedBodyAndExhaustedDeadlineNeverRetry() throws Exception {
        for(boolean body:new boolean[]{true,false}) {
            var count=new AtomicInteger();var scheduler=Executors.newSingleThreadScheduledExecutor();
            try {
                AgentModel model=(messages,l)->{count.incrementAndGet();if(body)l.delta("partial");l.error(new java.io.IOException());return ()->{};};
                var done=new CompletableFuture<String>();
                new AgentAttempts(model,new AgentProperties(),scheduler,Clock.systemUTC()).stream(List.of(UserMessage.from("hi")),Instant.now().plusMillis(100),listener(done));
                assertThat(done.get(1,TimeUnit.SECONDS)).isEqualTo("error");assertThat(count.get()).isOne();
            } finally {scheduler.shutdownNow();}
        }
    }
    @Test void duplicateAndLateCallbacksFromAFailedAttemptCannotFinishItsSuccessor() throws Exception {
        var scheduler=Executors.newSingleThreadScheduledExecutor();var calls=new AtomicInteger();
        var old=new java.util.concurrent.atomic.AtomicReference<AgentModel.Listener>();var second=new CountDownLatch(1);
        var p=new AgentProperties();p.setRetryBackoff(Duration.ofMillis(1));
        try {
            AgentModel model=(context,l)->{if(calls.incrementAndGet()==1){old.set(l);l.error(new java.io.IOException());l.error(new java.io.IOException());}
                else{second.countDown();}return ()->{};};
            var done=new CompletableFuture<String>();var call=new AgentAttempts(model,p,scheduler,Clock.systemUTC())
                .stream(List.of(UserMessage.from("hi")),Instant.now().plusSeconds(2),listener(done));
            assertThat(second.await(1,TimeUnit.SECONDS)).isTrue();old.get().complete(null);old.get().delta("late");
            assertThat(done).isNotDone();assertThat(calls.get()).isEqualTo(2);call.cancel();
        } finally {scheduler.shutdownNow();}
    }
    @Test void timeoutCancelsAHandleReturnedAfterTheTerminalCallback() throws Exception {
        var scheduler=Executors.newSingleThreadScheduledExecutor();var worker=Executors.newSingleThreadExecutor();
        var returned=new CountDownLatch(1);var cancelled=new CountDownLatch(1);var p=new AgentProperties();p.setFirstTokenTimeout(Duration.ofMillis(20));
        try {
            AgentModel model=(context,l)->{try{returned.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return cancelled::countDown;};
            var done=new CompletableFuture<String>();
            var future=worker.submit(()->new AgentAttempts(model,p,scheduler,Clock.systemUTC()).stream(List.of(UserMessage.from("hi")),Instant.now().plusSeconds(2),listener(done)));
            assertThat(done.get(1,TimeUnit.SECONDS)).isEqualTo("error");returned.countDown();future.get(1,TimeUnit.SECONDS);
            assertThat(cancelled.await(1,TimeUnit.SECONDS)).isTrue();
        } finally {returned.countDown();scheduler.shutdownNow();worker.shutdownNow();}
    }
    private AgentModel.Listener listener(CompletableFuture<String> done){return new AgentModel.Listener(){public void delta(String s){} public void complete(AgentModel.Usage u){done.complete("done");}public void error(Throwable e){done.complete("error");}};}
}
