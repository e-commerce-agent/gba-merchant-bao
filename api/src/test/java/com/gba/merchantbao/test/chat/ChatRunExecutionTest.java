package com.gba.merchantbao.chat.service;
// 上面 保留原 Java 包名
// 就  不把生产代码中少量原本只允许同包访问的类、构造方法等改为 public

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.chat.entity.ChatOwner;
import dev.langchain4j.data.message.UserMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 聊天结束时正确记录日志和指标、不泄漏密钥或消息正文、输出超限时取消模型调用
class ChatRunExecutionTest {
    @Test void terminalAuditAndMetricsContainMetadataButNeverCredentialsOrBody() {
        var service=mock(ChatService.class);var p=new AgentProperties();
        p.setApiKey("secret-api-key");p.setSystemPrompt("private-full-prompt");
        var run=new PreparedRun(new ChatOwner(11,21,"CONSUMER"),31,41,51,61,"STARTED",false,
                Instant.now().plusSeconds(30),List.of(UserMessage.from("13800138000 secret-user-body")));
        when(service.markStreaming(run)).thenReturn(true);
        when(service.finish(eq(run),eq("COMPLETED"),anyString(),isNull(),isNull(),eq(List.of()),eq(List.of()))).thenReturn(true);
        var registry=new SimpleMeterRegistry();var released=new AtomicInteger();var downstream=mock(AgentModel.Listener.class);
        Logger logger=(Logger)LoggerFactory.getLogger(ChatRunExecution.class);var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            var execution=new ChatRunExecution(service,run,"audit-request",p,downstream,released::incrementAndGet,Clock.systemUTC(),registry);
            execution.delta("private-answer");execution.complete(null);execution.delta("late");execution.error(new IllegalStateException("Bearer secret-access-token"));
            assertThat(released.get()).isOne();verify(downstream).complete(null);verify(downstream,never()).delta("late");
            assertThat(registry.get("merchant.agent.runs").tag("status","COMPLETED").counter().count()).isEqualTo(1);
            assertThat(registry.get("merchant.agent.duration").timer().count()).isOne();
            assertThat(registry.get("merchant.agent.first.token").timer().count()).isOne();
            String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("requestId=audit-request","runId=41","merchantId=11","role=CONSUMER","promptVersion=assistant-v1","firstTokenMs=","totalMs=","reason=completed")
                    .doesNotContain("secret-api-key","secret-access-token","private-full-prompt","secret-user-body","13800138000","private-answer");
        } finally {logger.detachAppender(appender);appender.stop();registry.close();}
    }
    @Test void outputLimitFailsAndCancelsTransportWithoutAcceptingLateCallbacks() {
        var service=mock(ChatService.class);var p=new AgentProperties();p.setMaxOutputChars(2);
        var run=new PreparedRun(new ChatOwner(11,21,"CONSUMER"),31,41,51,61,"STARTED",false,Instant.now().plusSeconds(30),List.of());
        when(service.markStreaming(run)).thenReturn(true);
        when(service.finish(run,"FAILED","好","DEPENDENCY_ERROR",null,List.of(),List.of())).thenReturn(true);
        var client=mock(AgentModel.Listener.class);var cancelled=new AtomicInteger();var released=new AtomicInteger();
        var execution=new ChatRunExecution(service,run,"limit",p,client,released::incrementAndGet,Clock.systemUTC(),null);
        execution.bind(cancelled::incrementAndGet);execution.delta("好");execution.delta("太长");execution.complete(null);
        verify(service).finish(run,"FAILED","好","DEPENDENCY_ERROR",null,List.of(),List.of());verify(client,never()).complete(any());
        assertThat(cancelled.get()).isOne();assertThat(released.get()).isOne();
    }
}
