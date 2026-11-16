package com.gba.merchantbao.chat.controller;
// 上面 保留原 Java 包名
// 就  不把生产代码中少量原本只允许同包访问的类、构造方法等改为 public

import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.chat.vo.ChatRunView;
import com.gba.merchantbao.common.api.ApiId;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

// ChatSseLifecycleTest：流式输出遇到慢客户端、断线或写入失败时能停止并释放资源，心跳不会变成聊天消息
class ChatSseLifecycleTest {
    @Test void aBlockedWriterExceedsItsTimeLimitAndReleasesTheWorker() throws Exception {
        var timer=Executors.newSingleThreadScheduledExecutor();var writer=Executors.newSingleThreadExecutor();
        var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);var failed=new AtomicInteger();
        var emitter=new SseEmitter(){
            @Override public void send(SseEventBuilder b)throws IOException {
                entered.countDown();try {new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();throw new IOException("closed");}
            }
            @Override public void completeWithError(Throwable e){}
        };
        try {
            var stream=session(emitter,4,writer,timer);stream.setCall(new AgentModel.Call(){public void cancel(){}public void fail(){failed.incrementAndGet();}});
            stream.startEvent();stream.start();assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.await(1,TimeUnit.SECONDS)).isTrue();assertThat(failed.get()).isOne();
            assertThat(writer.submit(()->"released").get(1,TimeUnit.SECONDS)).isEqualTo("released");
        } finally {timer.shutdownNow();writer.shutdownNow();}
    }
    @Test void boundedQueueRejectsSlowConsumersAndLateTokensAreDiscarded() throws Exception {
        var timer=Executors.newSingleThreadScheduledExecutor();var writer=Executors.newSingleThreadExecutor();var failed=new AtomicInteger();
        var emitter=new SseEmitter(){@Override public void completeWithError(Throwable e){}};
        try {
            var stream=session(emitter,1,writer,timer);stream.setCall(new AgentModel.Call(){public void cancel(){}public void fail(){failed.incrementAndGet();}});
            stream.startEvent();stream.listener().delta("first");stream.listener().delta("late");stream.listener().complete(null);
            assertThat(failed.get()).isOne();
        } finally {timer.shutdownNow();writer.shutdownNow();}
    }
    @Test void writeFailureRequestsCancellationAndStopsFurtherWrites() throws Exception {
        var timer=Executors.newSingleThreadScheduledExecutor();var writer=Executors.newSingleThreadExecutor();var cancelled=new CountDownLatch(1);var writes=new AtomicInteger();
        var emitter=new SseEmitter(){@Override public void send(SseEventBuilder b)throws IOException{writes.incrementAndGet();throw new IOException("broken pipe");}
            @Override public void completeWithError(Throwable e){}};
        try {
            var stream=session(emitter,4,writer,timer);stream.setCall(cancelled::countDown);stream.startEvent();stream.start();
            assertThat(cancelled.await(1,TimeUnit.SECONDS)).isTrue();stream.listener().delta("late");stream.listener().complete(null);
            writer.submit(()->{}).get(1,TimeUnit.SECONDS);assertThat(writes.get()).isOne();
        } finally {timer.shutdownNow();writer.shutdownNow();}
    }
    @Test void commentHeartbeatsDoNotCreateBusinessEvents() throws Exception {
        var timer=Executors.newSingleThreadScheduledExecutor();var writer=Executors.newSingleThreadExecutor();var heartbeat=new CountDownLatch(1);
        var emitter=new SseEmitter(){@Override public void send(SseEventBuilder b){heartbeat.countDown();}
            @Override public void completeWithError(Throwable e){}};
        try {var stream=session(emitter,4,writer,timer);stream.setCall(()->{});stream.start();assertThat(heartbeat.await(1,TimeUnit.SECONDS)).isTrue();}
        finally {timer.shutdownNow();writer.shutdownNow();}
    }
    private SseSession session(SseEmitter emitter,int capacity,ExecutorService writer,ScheduledExecutorService timer){
        return new SseSession(emitter,new ChatRunView(new ApiId(1),new ApiId(2),new ApiId(3),new ApiId(4),"STARTED"),"req",capacity,
                Duration.ofMillis(20),Duration.ofMillis(50),writer,timer);
    }
}
