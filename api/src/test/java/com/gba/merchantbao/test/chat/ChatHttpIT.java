package com.gba.merchantbao.test.chat;

import com.gba.merchantbao.test.support.DockerRequired;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.auth.enums.AccountRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.data.redis.host=127.0.0.1","spring.data.redis.port=1","merchant.agent.enabled=true",
        "merchant.agent.base-url=http://127.0.0.1:9/v1","merchant.agent.api-key=http-stub-key","merchant.agent.model=stub-model",
        "merchant.agent.allowed-origins[0]=http://localhost:5173","merchant.agent.heartbeat=50ms"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
class ChatHttpIT {
    static{DockerRequired.check();}
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34").withDatabaseName("chat_http").withUsername("test").withPassword("test").withReuse(false);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @LocalServerPort int port;
    @Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean SessionService sessions;
    @org.springframework.test.context.bean.override.mockito.MockitoBean AgentModel model;
    Actor actor;final AtomicInteger modelCalls=new AtomicInteger();
    @BeforeEach void setup(){
        modelCalls.set(0);
        long merchant=jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code='M_3C_DEMO'",Long.class);
        actor=new Actor(new AuthPrincipal(501,merchant,AccountRole.CONSUMER),"test");
        when(sessions.authenticate("http-test-token")).thenReturn(actor);
        when(sessions.authenticate(isNull())).thenThrow(com.gba.merchantbao.auth.exception.AuthError.unauthorized());
        when(model.stream(anyList(),any())).thenAnswer(invocation->{modelCalls.incrementAndGet();AgentModel.Listener l=invocation.getArgument(1);l.delta("hello");l.delta(" world");l.complete(null);return (AgentModel.Call)()->{};});
    }
    @Test void securityCorsJsonAndSseWorkThroughRealServletAsyncDispatch() throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var preflight=client.send(HttpRequest.newBuilder(uri("/api/v1/chat/sessions")).header("Origin","http://localhost:5173")
                    .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type,x-request-id").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(preflight.statusCode()).isEqualTo(200);assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
            var denied=client.send(HttpRequest.newBuilder(uri("/api/v1/chat/sessions")).header("Origin","https://evil.example").header("Access-Control-Request-Method","POST").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isEqualTo(403);
            var missing=client.send(HttpRequest.newBuilder(uri("/api/v1/chat/sessions")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(missing.statusCode()).isEqualTo(401);assertThat(json.readTree(missing.body()).path("code").asInt()).isEqualTo(10002);
            var created=send(client,"POST","/api/v1/chat/sessions","{}");assertThat(created.statusCode()).isEqualTo(201);
            String id=json.readTree(created.body()).path("data").path("sessionId").asText();assertThat(id).matches("[1-9][0-9]+");
            String path="/api/v1/chat/sessions/"+id+"/messages";
            String body="{\"content\":\"hello\",\"clientMessageId\":\""+UUID.randomUUID()+"\"}";
            var stream=send(client,"POST",path,body);assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(stream.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
            assertThat(stream.body()).contains("event:start","event:token","event:done","hello"," world","\"usage\":null").doesNotContain("event:error");
            var duplicate=send(client,"POST",path,body);assertThat(duplicate.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
            assertThat(json.readTree(duplicate.body()).path("data").path("status").asText()).isEqualTo("COMPLETED");assertThat(modelCalls.get()).isOne();
            var history=send(client,"GET",path,null);assertThat(json.readTree(history.body()).path("data").path("items").get(1).path("content").asText()).isEqualTo("hello world");
            var detail=send(client,"GET","/api/v1/chat/sessions/"+id,null);assertThat(json.readTree(detail.body()).path("data").path("activeRunId").isNull()).isTrue();
            var list=send(client,"GET","/api/v1/chat/sessions?size=1",null);assertThat(json.readTree(list.body()).path("data").path("items")).hasSize(1);
            var archive=send(client,"POST","/api/v1/chat/sessions/"+id+"/archive",null);
            assertThat(json.readTree(archive.body()).path("data").path("status").asText()).isEqualTo("ARCHIVED");
            var invalid=send(client,"GET",path+"?size=0",null);assertThat(invalid.statusCode()).isEqualTo(400);
            var invalidCursor=send(client,"GET",path+"?cursor=broken",null);assertThat(invalidCursor.statusCode()).isEqualTo(400);
        }
    }
    @Test void clientDisconnectCancelsUpstreamAndLeavesAQueryableTerminal() throws Exception {
        var cancelled=new java.util.concurrent.CountDownLatch(1);var late=new java.util.concurrent.atomic.AtomicReference<AgentModel.Listener>();
        doAnswer(invocation->{AgentModel.Listener l=invocation.getArgument(1);late.set(l);l.delta("partial");return (AgentModel.Call)cancelled::countDown;}).when(model).stream(anyList(),any());
        try(var client=HttpClient.newHttpClient()) {
            var created=send(client,"POST","/api/v1/chat/sessions","{}");String id=json.readTree(created.body()).path("data").path("sessionId").asText();
            var request=HttpRequest.newBuilder(uri("/api/v1/chat/sessions/"+id+"/messages")).header("Authorization","Bearer http-test-token")
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"abort\",\"clientMessageId\":\""+UUID.randomUUID()+"\"}")).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());assertThat(response.statusCode()).isEqualTo(200);
            try(var body=response.body()){assertThat(body.readNBytes(16)).hasSize(16);}
            assertThat(cancelled.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();late.get().delta("late");late.get().complete(null);
            var history=send(client,"GET","/api/v1/chat/sessions/"+id+"/messages",null);
            assertThat(json.readTree(history.body()).path("data").path("items").get(1).path("status").asText()).isEqualTo("CANCELLED");
        }
    }
    @Test void actualSpringdocGroupExportsSixOperationsAndBothSuccessMimeTypes() throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(HttpRequest.newBuilder(uri("/v3/api-docs.yaml/agent-conversation-v1")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            Map<String,Object> document=new org.yaml.snakeyaml.Yaml().load(response.body());
            var root=json.valueToTree(document);var paths=root.path("paths");assertThat(paths.size()).isEqualTo(4);
            int operations=0;for(var path:paths)for(String method:List.of("get","post"))if(path.has(method)){
                operations++;assertThat(path.path(method).path("security").toString()).contains("chatBearer");
                assertThat(path.path(method).path("responses").has("401")).isTrue();
                assertThat(path.path(method).path("responses").path("400").path("content").has("application/json")).isTrue();
            }
            assertThat(operations).isEqualTo(6);
            var send=paths.path("/api/v1/chat/sessions/{sessionId}/messages").path("post").path("responses").path("200").path("content");
            assertThat(send.has("application/json")).isTrue();assertThat(send.has("text/event-stream")).isTrue();
            assertThat(send.path("text/event-stream").path("examples").toString()).contains("event: start","event: token","event: done","event: error");
            var schemas=root.path("components").path("schemas");
            assertThat(schemas.path("ChatSession").path("properties").path("archivedAt").path("anyOf").get(1).path("type").asText()).isEqualTo("null");
            assertThat(schemas.path("ChatError").path("properties").path("data").path("type").asText()).isEqualTo("null");
            assertThat(response.body()).doesNotContain("http-stub-key","http-test-token");
            java.nio.file.Path rootDir=java.nio.file.Path.of("").toAbsolutePath();if(rootDir.getFileName().toString().equals("api"))rootDir=rootDir.getParent();
            var snapshot=rootDir.resolve("docs/api/agent-conversation-v1.openapi.yaml");java.nio.file.Files.createDirectories(snapshot.getParent());
            java.nio.file.Files.writeString(snapshot,response.body());
        }
    }
    URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
    HttpResponse<String> send(HttpClient client,String method,String path,String body)throws Exception {
        var builder=HttpRequest.newBuilder(uri(path)).timeout(java.time.Duration.ofSeconds(10)).header("Authorization","Bearer http-test-token")
                .header("X-Request-Id","http-contract").header("Accept","text/event-stream, application/json");
        if(body!=null)builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
