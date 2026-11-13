package com.gba.merchantbao.test.agent;

import com.gba.merchantbao.test.support.ScriptedAgentModel;

import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.AgentContext;
import com.gba.merchantbao.chat.entity.ChatMessageRow;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AgentContextTest {
    @Test void deterministicModelSupportsDeltasErrorsDelayAndCancellation() throws Exception {
        try(var model=new ScriptedAgentModel()) {
            var text=new StringBuilder(); var end=new java.util.concurrent.CompletableFuture<String>();
            com.gba.merchantbao.agent.service.AgentModel.Listener listener=new com.gba.merchantbao.agent.service.AgentModel.Listener(){
                public void delta(String s){text.append(s);}
                public void complete(com.gba.merchantbao.agent.service.AgentModel.Usage u){end.complete("done");}
                public void error(Throwable e){end.complete("error");}
            };
            model.failure=new java.io.IOException("preset"); model.stream(List.of(UserMessage.from("hi")),listener);
            assertThat(end.get(2,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("error");
            assertThat(text.toString()).isEqualTo("你好，请问有什么可以帮你？");
            model.delay=java.time.Duration.ofSeconds(5);
            model.stream(List.of(UserMessage.from("cancel")),listener).cancel();
            assertThat(model.cancellations.get()).isOne();
        }
    }
    @Test void onlyRecentCompleteSuccessfulPairsAreInjectedInChronologicalOrder() {
        var p = new AgentProperties(); p.setSystemPrompt("policy"); p.setHistoryMessages(2);
        var selected = new AgentContext(p).assemble(List.of(row(1,1,"USER","old","COMPLETED"),row(2,1,"ASSISTANT","old-a","COMPLETED"),
                row(3,2,"USER","bad","COMPLETED"),row(4,2,"ASSISTANT","partial","FAILED"),
                row(5,3,"USER","latest","COMPLETED"),row(6,3,"ASSISTANT","latest-a","COMPLETED")),"current");
        assertThat(selected).containsExactly(SystemMessage.from("policy"), UserMessage.from("latest"),AiMessage.from("latest-a"),UserMessage.from("current"));
    }
    @Test void budgetReservesOutputAndNeverTruncatesCurrentInputOrUsesHalfARound() {
        var p = new AgentProperties(); p.setSystemPrompt("s"); p.setContextTokens(35); p.setMaxOutputTokens(10);
        var c = new AgentContext(p);
        assertThat(c.assemble(List.of(row(1,1,"USER","中中中","COMPLETED"),row(2,1,"ASSISTANT","long-answer","COMPLETED")),"current"))
                .containsExactly(SystemMessage.from("s"),UserMessage.from("current"));
        assertThatThrownBy(() -> c.assemble(List.of(),"x".repeat(30))).hasMessage("AGENT_CONTEXT_TOO_LARGE");
        p.setHistoryMessages(1);
        assertThat(c.assemble(List.of(row(1,1,"USER","u","COMPLETED"),row(2,1,"ASSISTANT","a","COMPLETED")),"c")).hasSize(2);
    }
    private ChatMessageRow row(long seq,long run,String sender,String text,String status) {
        return new ChatMessageRow(seq,1,1,seq,sender,null,null,text,status,run,null,null,null,null,null,null,LocalDateTime.now(),LocalDateTime.now());
    }
}
