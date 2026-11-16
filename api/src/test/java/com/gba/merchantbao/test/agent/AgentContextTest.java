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
    @Test void evidenceHasPriorityOverWholeHistoryAndTheOriginalQuestionIsUntouched() {
        var p=new AgentProperties();p.setSystemPrompt("policy");p.setMaxOutputTokens(10);
        var context=new AgentContext(p);
        String question="  购买后七日内能退吗？😀\n保留原文  ";
        var accepted=context.assemble(List.of(row(1,1,"USER","history-question","COMPLETED"),
                row(2,1,"ASSISTANT","history-answer","COMPLETED")),question,true);
        var high=source(1,"正文😀");var low=source(2,"次要".repeat(100));
        var one=context.withEvidence(accepted,List.of(high));
        int required=one.messages().stream().filter(m->!(m instanceof AiMessage))
                .filter(m->!(m instanceof UserMessage u&&u.singleText().equals("history-question")))
                .mapToInt(m->(m instanceof SystemMessage s?s.text():((UserMessage)m).singleText()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length+4).sum()+10;
        p.setContextTokens(required);
        var grounded=context.withEvidence(accepted,List.of(high,low));
        assertThat(grounded.sources()).containsExactly(high);
        assertThat(grounded.messages()).hasSize(3);
        assertThat(grounded.messages().getFirst()).isEqualTo(accepted.getFirst());
        assertThat((UserMessage)grounded.messages().get(1)).extracting(UserMessage::singleText).asString()
                .contains("正文😀","reference_material","资料标题").doesNotContain(question);
        assertThat(grounded.messages().getLast()).isEqualTo(UserMessage.from(question));
        assertThat(grounded.messages().stream().filter(m->m.equals(UserMessage.from(question)))).hasSize(1);
        p.setContextTokens(required-1);
        assertThat(context.withEvidence(accepted,List.of(high)).sources()).isEmpty();
    }
    @Test void referenceLimitAndHistoryPairsRemainBounded() {
        var p=new AgentProperties();p.setSystemPrompt("policy");p.setHistoryMessages(2);
        var context=new AgentContext(p);
        var accepted=context.assemble(List.of(row(1,1,"USER","u","COMPLETED"),row(2,1,"ASSISTANT","a","COMPLETED")),"q",true);
        var grounded=context.withEvidence(accepted,java.util.stream.LongStream.rangeClosed(1,6).mapToObj(i->source(i,"text")).toList());
        assertThat(grounded.sources()).hasSize(5);
        assertThat(grounded.messages()).hasSize(5);
        assertThat(grounded.messages().subList(1,3)).containsExactly(UserMessage.from("u"),AiMessage.from("a"));
    }
    private com.gba.merchantbao.knowledge.vo.KnowledgeSource source(long id,String text) {
        return new com.gba.merchantbao.knowledge.vo.KnowledgeSource(new com.gba.merchantbao.common.api.ApiId(id),
                new com.gba.merchantbao.common.api.ApiId(id+10),"资料标题",1,text);
    }
}
