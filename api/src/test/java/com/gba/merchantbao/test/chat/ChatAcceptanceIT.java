package com.gba.merchantbao.test.chat;

import com.gba.merchantbao.test.support.DockerRequired;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.auth.service.SmsSender;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real login, JWT, Redis session validation, Servlet async SSE and MySQL, with only the external model/SMS replaced. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "merchant.agent.enabled=true","merchant.agent.base-url=http://127.0.0.1:9/v1",
    "merchant.agent.api-key=acceptance-test-only","merchant.agent.model=controlled-model",
    "merchant.agent.heartbeat=50ms","merchant.agent.first-token-timeout=2s",
    "merchant.agent.total-timeout=5s","merchant.agent.max-retries=0",
    "merchant.auth.namespace=chat-acceptance","merchant.auth.limits.login-ip-per-window=100"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts="/db/fixture/auth_accounts.sql",executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ChatAcceptanceIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34")
        .withDatabaseName("chat_acceptance").withUsername("test").withPassword("test").withReuse(false);
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379).withReuse(false);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        if(!MYSQL.isRunning())MYSQL.start();if(!REDIS.isRunning())REDIS.start();
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @MockitoBean AgentModel model; @MockitoBean SmsSender sms;
    final Map<String,String> codes=new ConcurrentHashMap<>();
    final AtomicInteger calls=new AtomicInteger();
    @BeforeEach void modelAndSms() {
        calls.set(0);
        doAnswer(i->{codes.put(i.getArgument(0),i.getArgument(1));return null;}).when(sms).sendLoginCode(anyString(),anyString());
        doAnswer(i->{calls.incrementAndGet();AgentModel.Listener l=i.getArgument(2);
            l.delta("hello");l.delta(" world");l.complete(null);return (AgentModel.Call)()->{};}).when(model).stream(anyList(),eq(true),any());
    }
    URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
    HttpRequest request(String token,String method,String path,String body) {
        var b=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("X-Request-Id","chat-acceptance")
            .header("Accept","text/event-stream, application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type","application/json");
        return b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
    }
    HttpResponse<String> call(String token,String method,String path,String body,int status)throws Exception {
        try(var client=HttpClient.newHttpClient()){
            var response=client.send(request(token,method,path,body),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s %s",method,path).isEqualTo(status);
            assertThat(response.headers().firstValue("X-Request-Id")).hasValue("chat-acceptance");
            return response;
        }
    }
    JsonNode data(HttpResponse<String> response)throws Exception{return json.readTree(response.body()).path("data");}
    String consumer(String merchant,String phone)throws Exception {
        call(null,"POST","/api/v1/stores/"+merchant+"/auth/sms-codes",json.writeValueAsString(Map.of("phone",phone)),200);
        return data(call(null,"POST","/api/v1/stores/"+merchant+"/auth/consumer/login",
            json.writeValueAsString(Map.of("phone",phone,"code",codes.get(phone))),200)).path("accessToken").asText();
    }
    String staff(String merchant)throws Exception {
        return data(call(null,"POST","/api/v1/auth/staff/login",json.writeValueAsString(Map.of(
            "merchantCode",merchant,"identifier","fixture_staff","password","FixtureOnly-Auth-2026!")),200)).path("accessToken").asText();
    }
    String create(String token)throws Exception{return data(call(token,"POST","/api/v1/chat/sessions","{}",201)).path("sessionId").asText();}
    String body(String content,String key)throws Exception{return json.writeValueAsString(Map.of("content",content,"clientMessageId",key));}
    String path(String id){return "/api/v1/chat/sessions/"+id;}
    JsonNode assistant(String token,String id)throws Exception{return data(call(token,"GET",path(id)+"/messages",null,200)).path("items").get(1);}
    void assertStored(String id,String run,String state,String text) {
        var row=jdbc.queryForMap("SELECT status,content FROM chat_message WHERE session_id=? AND run_id=? AND sender_type='ASSISTANT'",id,run);
        assertThat(row.get("status")).isEqualTo(state);assertThat(row.get("content")).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT active_run_id FROM chat_session WHERE id=?",Long.class,id)).isNull();
    }
    @Test void loginToStreamToHistoryAndArchivePreservesIdentityAndIdempotency()throws Exception {
        String token=consumer("M_3C_DEMO","+8613900000501"),id=create(token),key=UUID.randomUUID().toString();
        String body=body("hello",key);
        var stream=call(token,"POST",path(id)+"/messages",body,200);
        assertThat(stream.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        List<String> events=stream.body().lines().filter(s->s.startsWith("event:")).map(s->s.substring(6).trim()).toList();
        assertThat(events).containsExactly("start","token","token","done");
        var message=assistant(token,id);assertThat(message.path("content").asText()).isEqualTo("hello world");
        assertStored(id,message.path("runId").asText(),"COMPLETED","hello world");
        var duplicate=call(token,"POST",path(id)+"/messages",body,200);
        assertThat(duplicate.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(data(duplicate).path("runId")).isEqualTo(message.path("runId"));assertThat(calls.get()).isOne();
        call(token,"POST",path(id)+"/messages",body("different",key),409);
        assertThat(data(call(token,"GET","/api/v1/chat/sessions?size=100",null,200)).path("items").toString()).contains(id);
        call(token,"GET",path(id),null,200);
        var archived=data(call(token,"POST",path(id)+"/archive",null,200));
        assertThat(data(call(token,"POST",path(id)+"/archive",null,200))).isEqualTo(archived);
        call(token,"POST",path(id)+"/messages",body("new",UUID.randomUUID().toString()),409);
    }
    @Test void realStaffAndOtherTenantCannotReadOrModifyAnotherOwnersSession()throws Exception {
        String owner=consumer("M_3C_DEMO","+8613900000502"),id=create(owner);
        String staff=staff("M_3C_DEMO"),other=staff("M_ISOLATION_TEST"),peer=consumer("M_3C_DEMO","+8613900000503");
        create(staff);
        for(String wrong:List.of(staff,other,peer)){
            call(wrong,"GET",path(id),null,404);call(wrong,"GET",path(id)+"/messages",null,404);
            call(wrong,"POST",path(id)+"/archive",null,404);
            call(wrong,"POST",path(id)+"/messages",body("forbidden",UUID.randomUUID().toString()),404);
        }
        call(null,"GET",path(id),null,401);call("not-a-jwt","GET",path(id),null,401);
        call(owner,"POST",path(id)+"/messages","{\"content\":\"x\",\"clientMessageId\":\""+UUID.randomUUID()+"\",\"merchantId\":\"1\"}",400);
        assertThat(calls.get()).isZero();
        call(owner,"POST","/api/v1/auth/logout","{}",200);call(owner,"GET",path(id),null,401);
    }
    @Test void busyRetryTimeoutAndDependencyFailureRemainQueryableThroughRealAuthentication()throws Exception {
        var cancelled=new CountDownLatch(1);var began=new CountDownLatch(1);
        doAnswer(i->{calls.incrementAndGet();began.countDown();return (AgentModel.Call)cancelled::countDown;}).when(model).stream(anyList(),eq(true),any());
        String token=consumer("M_3C_DEMO","+8613900000504"),id=create(token),key=UUID.randomUUID().toString(),body=body("wait",key);
        try(var client=HttpClient.newHttpClient()){
            var pending=client.sendAsync(request(token,"POST",path(id)+"/messages",body),HttpResponse.BodyHandlers.ofString());
            assertThat(began.await(3,TimeUnit.SECONDS)).isTrue();
            call(token,"POST",path(id)+"/messages",body,200);
            call(token,"POST",path(id)+"/messages",body("other",UUID.randomUUID().toString()),409);
            call(token,"POST",path(id)+"/archive",null,409);
            String stream=pending.get(8,TimeUnit.SECONDS).body();assertThat(stream).contains("event:error","TIMEOUT").doesNotContain("event:done");
            assertThat(cancelled.await(2,TimeUnit.SECONDS)).isTrue();var message=assistant(token,id);
            assertStored(id,message.path("runId").asText(),"TIMED_OUT","");assertThat(calls.get()).isOne();
        }
        doAnswer(i->{AgentModel.Listener l=i.getArgument(2);l.delta("partial");l.error(new IllegalStateException("test upstream failure"));return (AgentModel.Call)()->{};}).when(model).stream(anyList(),eq(true),any());
        String failed=create(token);var stream=call(token,"POST",path(failed)+"/messages",body("fail",UUID.randomUUID().toString()),200);
        assertThat(stream.body()).contains("event:error","DEPENDENCY_ERROR").doesNotContain("event:done","test upstream failure");
        var message=assistant(token,failed);assertStored(failed,message.path("runId").asText(),"FAILED","partial");
    }
    @Test void disconnectCancelsAndRevokedTokenCannotRecoverHistory()throws Exception {
        var cancelled=new CountDownLatch(1);var listener=new AtomicReference<AgentModel.Listener>();
        doAnswer(i->{AgentModel.Listener l=i.getArgument(2);listener.set(l);l.delta("partial");return (AgentModel.Call)cancelled::countDown;}).when(model).stream(anyList(),eq(true),any());
        String token=consumer("M_3C_DEMO","+8613900000505"),id=create(token);
        try(var client=HttpClient.newHttpClient()){
            var response=client.send(request(token,"POST",path(id)+"/messages",body("abort",UUID.randomUUID().toString())),HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);try(var input=response.body()){assertThat(input.readNBytes(16)).hasSize(16);}
        }
        assertThat(cancelled.await(4,TimeUnit.SECONDS)).isTrue();listener.get().delta("late");listener.get().complete(null);
        var message=assistant(token,id);assertStored(id,message.path("runId").asText(),"CANCELLED","partial");
        call(token,"POST","/api/v1/auth/logout","{}",200);call(token,"GET",path(id)+"/messages",null,401);
    }
}
