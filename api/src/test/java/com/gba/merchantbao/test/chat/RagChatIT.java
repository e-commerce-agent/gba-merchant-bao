package com.gba.merchantbao.test.chat;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.chat.mapper.ChatMessageMapper;
import com.gba.merchantbao.chat.service.*;
import com.gba.merchantbao.chat.vo.ChatMessageView;
import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.mapper.KnowledgeMapper;
import com.gba.merchantbao.knowledge.service.*;
import com.gba.merchantbao.knowledge.vo.KnowledgeSource;
import com.gba.merchantbao.test.support.DockerRequired;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real MySQL, acceptance/runtime and servlet SSE; deterministic external dependency doubles. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.data.redis.host=127.0.0.1","spring.data.redis.port=1","merchant.agent.enabled=true",
        "merchant.agent.base-url=http://127.0.0.1:9/v1","merchant.agent.api-key=rag-stub-key",
        "merchant.agent.model=stub-model","merchant.agent.heartbeat=30ms"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
class RagChatIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("rag_chat").withUsername("test").withPassword("test").withReuse(false);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);
        r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired ChatService chat;
    @Autowired RagProperties rag; @Autowired AgentProperties agent;
    @MockitoBean SessionService sessions; @MockitoBean AgentModel model;
    @MockitoBean KnowledgeEmbedding embedding; @MockitoBean MilvusKnowledgeStore vectors;
    @MockitoSpyBean KnowledgeMapper knowledge; @MockitoSpyBean ChatMessageMapper messages;
    Actor owner;long merchant,document,chunk;
    static final String QUESTION="  七日内能退吗？😀\n请保留换行  ";
    static final String EXCERPT="购买后七日内，未拆封商品可申请退货。😀";
    @BeforeEach void setup() {
        merchant=jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code='M_3C_DEMO'",Long.class);
        owner=new Actor(new AuthPrincipal(7301,merchant,AccountRole.CONSUMER),"rag-session");
        when(sessions.authenticate("rag-token")).thenReturn(owner);
        rag.setEnabled(true);rag.setTopK(3);agent.setTotalTimeout(Duration.ofSeconds(10));agent.setContextTokens(8192);
        document=jdbc.queryForObject("SELECT COALESCE(MAX(id),70000)+10 FROM knowledge_document",Long.class);chunk=document+1;
        jdbc.update("INSERT INTO knowledge_document VALUES (?,?,?,'READY',1,7301,UTC_TIMESTAMP(3))",document,merchant,"售后政策");
        jdbc.update("INSERT INTO knowledge_chunk VALUES (?,?,?,?,?,UTC_TIMESTAMP(3))",chunk,merchant,document,1,EXCERPT);
        when(embedding.embed(anyList(),any())).thenReturn(List.of(List.of(1f,0f)));
        when(vectors.search(eq(merchant),anyList(),any())).thenReturn(List.of(new MilvusKnowledgeStore.Hit(chunk,document,.95f)));
        when(model.stream(anyList(),any())).thenAnswer(i->{AgentModel.Listener l=i.getArgument(1);
            l.delta("按资料可申请退货");l.complete(new AgentModel.Usage(100L,10L));return (AgentModel.Call)()->{};});
    }
    @AfterEach void restore(){rag.setEnabled(false);agent.setTotalTimeout(Duration.ofSeconds(90));agent.setContextTokens(8192);}
    @Test void sseAndHistoryExposeTheExactCommittedEvidenceAndDuplicateDoesNotRerun() throws Exception {
        var input=new AtomicReference<List<ChatMessage>>();
        when(embedding.embed(anyList(),any())).thenAnswer(i->{
            // Accepted placeholders already committed before retrieval.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE content=?",Long.class,QUESTION)).isPositive();
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(List.of(1f,0f));
        });
        doAnswer(i->{
            input.set(List.copyOf(i.getArgument(0)));AgentModel.Listener l=i.getArgument(1);
            l.delta("answer");l.complete(null);return (AgentModel.Call)()->{};
        }).when(model).stream(anyList(),any());
        long id=chat.create(owner,null).id();String key=UUID.randomUUID().toString(),body=body(key);
        try(var client=HttpClient.newHttpClient()) {
            var response=send(client,"POST",path(id),body);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("event:start","event:token","event:done").doesNotContain("event:error");
            var done=event(response.body(),"done");var saved=chat.history(owner,id,null,20).items().getLast();
            assertThat(saved.content()).isEqualTo("answer");assertThat(saved.status()).isEqualTo("COMPLETED");
            assertThat(done.path("sources")).isEqualTo(json.readTree(saved.sourcesJson())).hasSize(1);
            var source=done.path("sources").get(0);
            assertThat(source.path("documentId").asText()).isEqualTo(Long.toString(document));
            assertThat(source.path("chunkId").asText()).isEqualTo(Long.toString(chunk));
            assertThat(source.path("title").asText()).isEqualTo("售后政策");
            assertThat(source.path("excerpt").asText()).isEqualTo(EXCERPT);assertThat(source.path("chunkNo").asInt()).isOne();
            assertThat(input.get().getFirst()).isInstanceOf(SystemMessage.class);
            assertThat(((SystemMessage)input.get().getFirst()).text()).doesNotContain(EXCERPT).contains("只依据本次参考资料");
            assertThat(((UserMessage)input.get().get(1)).singleText()).contains(EXCERPT,"售后政策",Long.toString(chunk));
            assertThat(input.get().getLast()).isEqualTo(UserMessage.from(QUESTION));
            assertThat(input.get().stream().filter(m->m.equals(UserMessage.from(QUESTION)))).hasSize(1);
            jdbc.update("UPDATE knowledge_document SET title='changed',status='FAILED' WHERE id=?",document);
            jdbc.update("UPDATE knowledge_chunk SET content='changed' WHERE id=?",chunk);
            rag.setEnabled(false);
            var history=json.readTree(send(client,"GET",path(id),null).body()).path("data").path("items");
            assertThat(history.get(0).path("sources")).isEmpty();
            assertThat(history.get(1).path("sources")).isEqualTo(done.path("sources"));
            var duplicate=send(client,"POST",path(id),body);
            assertThat(duplicate.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
            assertThat(json.readTree(duplicate.body()).path("data").path("runId").asText()).isEqualTo(done.path("runId").asText());
            verify(embedding).embed(eq(List.of(QUESTION)),any());verify(vectors).search(eq(merchant),anyList(),any());verify(model).stream(anyList(),any());
            var other=new Actor(new AuthPrincipal(7302,merchant,AccountRole.CONSUMER),"other");
            assertThatThrownBy(()->chat.history(other,id,null,20)).hasMessage("resource not found");
        }
    }
    @Test void noMatchAndNoEvidenceFitsCompleteWithoutCallingTheModel() throws Exception {
        for(boolean tooLarge:List.of(false,true)) {
            if(!tooLarge)when(vectors.search(eq(merchant),anyList(),any())).thenReturn(List.of());
            else {
                when(vectors.search(eq(merchant),anyList(),any())).thenReturn(List.of(new MilvusKnowledgeStore.Hit(chunk,document,.95f)));
                // Mandatory system + original question fit, reference envelope and text do not.
                agent.setContextTokens(agent.getMaxOutputTokens()+450);
            }
            var run=accept();var result=execute(run).get(3,TimeUnit.SECONDS);
            assertThat(result.error()).isNull();assertThat(result.sources()).isEmpty();
            var saved=last(run);assertThat(saved.content()).isEqualTo(KnowledgeRetrieval.NO_MATCH);
            assertThat(ChatMessageView.from(saved).sources()).isEmpty();
        }
        verifyNoInteractions(model);
    }
    @Test void dependencyFailuresNeverBecomeNoMatchAndLeaveEmptySources() throws Exception {
        for(String stage:List.of("embedding","vectors","mysql","generation")) {
            reset(embedding,vectors,model);clearInvocations(knowledge);
            when(embedding.embed(anyList(),any())).thenReturn(List.of(List.of(1f,0f)));
            when(vectors.search(eq(merchant),anyList(),any())).thenReturn(List.of(new MilvusKnowledgeStore.Hit(chunk,document,.95f)));
            if(stage.equals("embedding"))when(embedding.embed(anyList(),any())).thenThrow(new IllegalStateException("embedding failed"));
            if(stage.equals("vectors"))when(vectors.search(anyLong(),anyList(),any())).thenThrow(new IllegalStateException("vector failed"));
            if(stage.equals("mysql"))doThrow(new org.springframework.dao.DataAccessResourceFailureException("mysql failed")).when(knowledge).readyReferences(anyLong(),anyList());
            if(stage.equals("generation"))when(model.stream(anyList(),any())).thenAnswer(i->{AgentModel.Listener l=i.getArgument(1);l.delta("partial");l.error(new IllegalStateException("bad generation"));return (AgentModel.Call)()->{};});
            try {
                var run=accept();var result=execute(run).get(3,TimeUnit.SECONDS);
                assertThat(result.error()).isEqualTo("DEPENDENCY_ERROR");assertThat(last(run).status()).isEqualTo("FAILED");
                assertThat(ChatMessageView.from(last(run)).sources()).isEmpty();
                assertThat(last(run).content()).doesNotContain(KnowledgeRetrieval.NO_MATCH);
                if(!stage.equals("generation"))verifyNoInteractions(model);
            } finally {reset(knowledge);}
        }
    }
    @Test void generationRetryReusesTheSameRetrievedContext() throws Exception {
        var count=new AtomicInteger();var prompts=new CopyOnWriteArrayList<List<ChatMessage>>();
        doAnswer(i->{
            prompts.add(i.getArgument(0));AgentModel.Listener l=i.getArgument(1);
            if(count.incrementAndGet()==1)l.error(new RuntimeException(new java.io.IOException("retry")));
            else {l.delta("retried");l.complete(null);}return (AgentModel.Call)()->{};
        }).when(model).stream(anyList(),any());
        var run=accept();assertThat(execute(run).get(3,TimeUnit.SECONDS).error()).isNull();
        assertThat(prompts).hasSize(2);assertThat(prompts.get(0)).isSameAs(prompts.get(1));
        verify(embedding).embed(anyList(),any());verify(vectors).search(anyLong(),anyList(),any());
        assertThat(ChatMessageView.from(last(run)).sources()).hasSize(1);
    }
    @Test void retrievalSharesTheSavedDeadlineAndLateResultsCannotStartGeneration() throws Exception {
        agent.setTotalTimeout(Duration.ofMillis(700));
        var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);
        when(embedding.embed(anyList(),any())).thenAnswer(i->{
            entered.countDown();try{new CountDownLatch(1).await(4,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.countDown();}
            return List.of(List.of(1f,0f));
        });
        var run=accept();var end=execute(run);
        assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
        assertThat(end.get(3,TimeUnit.SECONDS).error()).isEqualTo("TIMEOUT");
        assertThat(interrupted.await(2,TimeUnit.SECONDS)).isTrue();
        assertThat(last(run).status()).isEqualTo("TIMED_OUT");assertThat(ChatMessageView.from(last(run)).sources()).isEmpty();
        verifyNoInteractions(model,vectors);
    }
    @Test void servletDisconnectDuringRetrievalCancelsAndNeverStartsGeneration() throws Exception {
        var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);
        when(embedding.embed(anyList(),any())).thenAnswer(i->{
            entered.countDown();try{new CountDownLatch(1).await(8,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.countDown();}
            return List.of(List.of(1f,0f));
        });
        long id=chat.create(owner,null).id();
        try(var client=HttpClient.newHttpClient()) {
            var request=HttpRequest.newBuilder(uri(path(id))).header("Authorization","Bearer rag-token").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(UUID.randomUUID().toString()))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            try(var stream=response.body()){assertThat(stream.readNBytes(16)).hasSize(16);}
            assertThat(interrupted.await(5,TimeUnit.SECONDS)).isTrue();
            var saved=chat.history(owner,id,null,20).items().getLast();
            assertThat(saved.status()).isEqualTo("CANCELLED");assertThat(ChatMessageView.from(saved).sources()).isEmpty();
            verifyNoInteractions(model,vectors);
        }
    }
    @Test void sourcesFailureRollsBackBodyAndRunReleaseAndSendsErrorInsteadOfDone() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("snapshot failure")).when(messages)
                .saveSourcesOwned(any(),anyLong(),anyLong(),anyLong(),anyString());
        var run=accept();
        try {
            assertThat(execute(run).get(3,TimeUnit.SECONDS).error()).isEqualTo("CHAT_DEPENDENCY_UNAVAILABLE");
            assertThat(last(run).status()).isEqualTo("STREAMING");assertThat(last(run).content()).isEmpty();assertThat(last(run).sourcesJson()).isNull();
            assertThat(chat.detail(owner,run.sessionId()).activeRunId()).isEqualTo(run.runId());
        } finally {reset(messages);chat.finish(run,"FAILED","","DEPENDENCY_ERROR",null);}
    }
    @Test void disabledRagAndLegacyNullRowsExposeEmptySources() throws Exception {
        rag.setEnabled(false);var run=accept();
        assertThat(execute(run).get(3,TimeUnit.SECONDS).sources()).isEmpty();verifyNoInteractions(embedding,vectors);
        jdbc.update("UPDATE chat_message SET sources_json=NULL WHERE session_id=?",run.sessionId());
        assertThat(chat.history(owner,run.sessionId(),null,20).items()).allSatisfy(r->assertThat(ChatMessageView.from(r).sources()).isEmpty());
    }
    PreparedRun accept(){return chat.accept(owner,chat.create(owner,null).id(),UUID.randomUUID().toString(),QUESTION);}
    com.gba.merchantbao.chat.entity.ChatMessageRow last(PreparedRun run){return chat.history(owner,run.sessionId(),null,20).items().getLast();}
    record Result(String error,List<KnowledgeSource> sources){}
    CompletableFuture<Result> execute(PreparedRun run) {
        var end=new CompletableFuture<Result>();
        chat.execute(run,"rag-test",new ChatRunListener(){
            public void delta(String d){}
            public void complete(AgentModel.Usage u,List<KnowledgeSource> sources){
                assertThat(ChatMessageView.from(last(run)).sources()).isEqualTo(sources);end.complete(new Result(null,sources));
            }
            public void error(Throwable e){end.complete(new Result(e.getMessage(),List.of()));}
        });return end;
    }
    String path(long id){return "/api/v1/chat/sessions/"+id+"/messages";}
    String body(String key)throws Exception{return json.writeValueAsString(Map.of("content",QUESTION,"clientMessageId",key));}
    URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
    HttpResponse<String> send(HttpClient c,String method,String path,String body)throws Exception {
        var b=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer rag-token").header("Content-Type","application/json");
        return c.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode event(String stream,String name)throws Exception {
        for(String block:stream.split("\n\n"))if(block.contains("event:"+name))
            for(String line:block.split("\n"))if(line.startsWith("data:"))return json.readTree(line.substring(5));
        throw new AssertionError("Missing event "+name);
    }
}
