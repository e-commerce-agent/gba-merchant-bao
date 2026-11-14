package com.gba.merchantbao.test.knowledge;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.test.support.DockerRequired;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real HTTP/SDK/MySQL/Milvus pipeline. Only authentication and remote model protocols are controlled. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",
        "merchant.rag.enabled=true", "merchant.agent.enabled=true",
        "merchant.rag.embedding-api-key=pipeline-embedding-test", "merchant.rag.embedding-model=pipeline-embedding",
        "merchant.rag.embedding-dimension=4", "merchant.rag.collection=pipeline_test",
        "merchant.agent.api-key=pipeline-chat-test", "merchant.agent.model=pipeline-chat"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
class RagPipelineIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34").withReuse(false);
    static final MilvusFixture MILVUS=new MilvusFixture();
    static final ObjectMapper JSON=new ObjectMapper();
    static final AtomicInteger EMBEDDINGS=new AtomicInteger(), GENERATIONS=new AtomicInteger();
    static final AtomicReference<Throwable> PROTOCOL_FAILURE=new AtomicReference<>();
    static HttpServer provider;
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) throws Exception {
        MILVUS.start(); provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/v1/embeddings",exchange->{
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer pipeline-embedding-test");
                var request=JSON.readTree(exchange.getRequestBody());
                assertThat(request.path("dimensions").asInt()).isEqualTo(4);
                assertThat(request.path("model").asText()).isEqualTo("pipeline-embedding");
                var inputs=request.path("input");var data=new ArrayList<Map<String,Object>>();
                for(int i=0;i<inputs.size();i++) {
                    String input=inputs.get(i).asText();EMBEDDINGS.incrementAndGet();
                    int axis=input.contains("路由器R9000")?3:input.contains("退货")||input.contains("拆封")?1:
                            input.contains("SKU-CHG-")||input.contains("SKU-CBL-")?0:2;
                    var vector=new ArrayList<Integer>(List.of(0,0,0,0));vector.set(axis,1);
                    data.add(Map.of("object","embedding","index",i,"embedding",vector));
                }
                var bytes=JSON.writeValueAsBytes(Map.of("object","list","data",data,"model","pipeline-embedding",
                        "usage",Map.of("prompt_tokens",10,"total_tokens",10)));
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch(Throwable failure) { PROTOCOL_FAILURE.set(failure);exchange.sendResponseHeaders(500,-1); }
            finally { exchange.close(); }
        });
        provider.createContext("/v1/chat/completions",exchange->{
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer pipeline-chat-test");
                var request=JSON.readTree(exchange.getRequestBody());var messages=request.path("messages");
                assertThat(request.path("stream").asBoolean()).isTrue();
                assertThat(request.path("model").asText()).isEqualTo("pipeline-chat");
                assertThat(messages.size()).isBetween(2,3);
                assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
                String evidence=messages.get(1).path("content").asText();
                assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
                if(messages.size()==3) {
                    assertThat(evidence).startsWith("<reference_material>\n").endsWith("</reference_material>");
                    String question=messages.get(2).path("content").asText();
                    assertThat(evidence).doesNotContain(question);
                } else assertThat(evidence).isNotBlank();
                GENERATIONS.incrementAndGet();
                // Return the supplied evidence verbatim, proving that the actual wire request carried it.
                String answer=messages.size()==2?(evidence.equals("关闭RAG后的普通问题")?"普通回答":
                        com.gba.merchantbao.knowledge.service.KnowledgeRetrieval.NO_MATCH):evidence.substring(evidence.indexOf('\n',evidence.indexOf("[documentId="))+1,
                        evidence.lastIndexOf("</reference_material>")).strip();
                String delta=JSON.writeValueAsString(Map.of("id","pipeline","object","chat.completion.chunk","model","pipeline-chat",
                        "choices",List.of(Map.of("index",0,"delta",Map.of("role","assistant","content",answer)))));
                String end=JSON.writeValueAsString(Map.of("id","pipeline","object","chat.completion.chunk","model","pipeline-chat",
                        "choices",List.of(Map.of("index",0,"delta",Map.of(),"finish_reason","stop"))));
                byte[] bytes=("data: "+delta+"\n\ndata: "+end+"\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch(Throwable failure) { PROTOCOL_FAILURE.set(failure);exchange.sendResponseHeaders(500,-1); }
            finally { exchange.close(); }
        });
        provider.start();
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("merchant.rag.milvus-uri",MILVUS::uri);
        r.add("merchant.rag.embedding-base-url",()->"http://127.0.0.1:"+provider.getAddress().getPort()+"/v1");
        r.add("merchant.agent.base-url",()->"http://127.0.0.1:"+provider.getAddress().getPort()+"/v1");
    }
    @AfterAll static void stop(){if(provider!=null)provider.stop(0);MILVUS.close();}
    @LocalServerPort int port;
    @Autowired RagProperties properties;
    @MockitoBean SessionService sessions;

    @Test void threeDocumentsAndFiveQuestionsTraverseActualSdkVectorSqlSseAndHistory() throws Exception {
        long merchant=1000000000000001L;
        when(sessions.authenticate("staff")).thenReturn(new Actor(new AuthPrincipal(101,merchant,AccountRole.MERCHANT_STAFF),"pipeline"));
        when(sessions.authenticate("consumer")).thenReturn(new Actor(new AuthPrincipal(102,merchant,AccountRole.CONSUMER),"pipeline"));
        when(sessions.authenticate("other")).thenReturn(new Actor(new AuthPrincipal(103,1000000000000002L,AccountRole.CONSUMER),"pipeline"));
        var examples=Path.of("docs/rag/examples");var results=new ArrayList<Map<String,Object>>();
        try(var client=HttpClient.newHttpClient()) {
            for(var file:Map.of("product.md","商品资料","after-sales.txt","售后政策","store-rules.md","店铺规则").entrySet()) {
                String boundary="pipeline-boundary";
                String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n"+file.getValue()
                        +"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+file.getKey()+"\"\r\nContent-Type: text/plain\r\n\r\n"
                        +Files.readString(examples.resolve(file.getKey()))+"\r\n--"+boundary+"--\r\n";
                var response=send(client,"POST","/api/v1/knowledge/documents","staff",body,"multipart/form-data; boundary="+boundary);
                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(JSON.readTree(response.body()).path("data").path("status").asText()).isEqualTo("READY");
            }
            assertThat(JSON.readTree(send(client,"GET","/api/v1/knowledge/documents","staff",null,"application/json").body()).path("data").path("items")).hasSize(3);
            for(var testCase:JSON.readTree(Files.readString(examples.resolve("questions.json")))) {
                String question=testCase.path("question").asText();boolean noMatch=testCase.path("noMatch").asBoolean();
                var result=ask(client,"consumer",question);var done=result.get("done");
                if(noMatch) {
                    assertThat(done.path("sources")).isEmpty();
                    assertThat(result.get("history").path("content").asText()).isEqualTo(testCase.path("expected").asText());
                } else {
                    assertThat(done.path("sources")).hasSize(1);
                    assertThat(done.path("sources").get(0).path("title").asText()).isEqualTo(testCase.path("title").asText());
                    assertThat(result.get("history").path("content").asText()).isEqualTo(done.path("sources").get(0).path("excerpt").asText());
                }
                results.add(Map.of("question",question,"sources",done.path("sources"),"answer",result.get("history").path("content"),"passed",true));
            }
            int before=GENERATIONS.get();
            assertThat(ask(client,"other","SKU-CHG-65W-CN的功率是什么？").get("done").path("sources")).isEmpty();
            assertThat(GENERATIONS.get()).isEqualTo(before+1);
            properties.setEnabled(false);
            try { assertThat(ask(client,"consumer","关闭RAG后的普通问题").get("done").path("sources")).isEmpty(); }
            finally { properties.setEnabled(true); }
            assertThat(GENERATIONS.get()).isEqualTo(7);
            assertThat(EMBEDDINGS.get()).isEqualTo(9); // three documents, five questions, one isolated merchant; disabled skips embedding
            assertThat(PROTOCOL_FAILURE.get()).isNull();
            Files.createDirectories(Path.of("target/rag-pipeline"));
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/rag-pipeline/results.json").toFile(),
                    Map.of("mode","real MySQL/Milvus and model SDKs; controlled HTTP model protocols; NOT real provider quality", "cases",results));
        }
    }
    Map<String,JsonNode> ask(HttpClient client,String token,String question) throws Exception {
        var created=send(client,"POST","/api/v1/chat/sessions",token,"{}","application/json");assertThat(created.statusCode()).isEqualTo(201);
        String path="/api/v1/chat/sessions/"+JSON.readTree(created.body()).path("data").path("sessionId").asText()+"/messages";
        String body=JSON.writeValueAsString(Map.of("content",question,"clientMessageId",UUID.randomUUID().toString()));
        var response=send(client,"POST",path,token,body,"application/json");assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("event:start","event:done").doesNotContain("event:error");
        JsonNode done=null;
        for(String frame:response.body().split("\\r?\\n\\r?\\n")) if(frame.contains("event:done")) {
            String data=frame.lines().filter(line->line.startsWith("data:")).findFirst().orElseThrow().substring(5).strip();done=JSON.readTree(data);
        }
        assertThat(done).isNotNull();
        var history=JSON.readTree(send(client,"GET",path,token,null,"application/json").body()).path("data").path("items").get(1);
        assertThat(history.path("sources")).isEqualTo(done.path("sources"));
        int embeds=EMBEDDINGS.get(),generations=GENERATIONS.get();
        var duplicate=JSON.readTree(send(client,"POST",path,token,body,"application/json").body()).path("data");
        assertThat(duplicate.path("runId")).isEqualTo(done.path("runId"));
        assertThat(EMBEDDINGS.get()).isEqualTo(embeds);assertThat(GENERATIONS.get()).isEqualTo(generations);
        return Map.of("done",done,"history",history);
    }
    HttpResponse<String> send(HttpClient client,String method,String path,String token,String body,String mime) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization","Bearer "+token).header("Accept","application/json, text/event-stream");
        if(body!=null)request.header("Content-Type",mime);
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
