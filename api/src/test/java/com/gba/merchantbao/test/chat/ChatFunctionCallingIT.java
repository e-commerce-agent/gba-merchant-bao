package com.gba.merchantbao.test.chat;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.service.*;
import com.gba.merchantbao.test.support.DockerRequired;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 隔离 MySQL + 真实模型适配器和订单服务；仅认证会话、向量服务及模型 HTTP 外部端点使用确定性桩。 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.data.redis.host=127.0.0.1","spring.data.redis.port=1","merchant.agent.enabled=true",
        "merchant.agent.api-key=integration-stub-only","merchant.agent.model=protocol-stub",
        "merchant.agent.max-retries=0","merchant.agent.total-timeout=15s","merchant.agent.first-token-timeout=10s"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Sql(scripts={"/db/fixture/auth_accounts.sql","/db/fixture/product_catalog.sql","/db/fixture/orders.sql"},
        executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ChatFunctionCallingIT {
    static { DockerRequired.check(); }
    static final ObjectMapper JSON=new ObjectMapper();
    static final List<JsonNode> REQUESTS=new CopyOnWriteArrayList<>();
    static final HttpServer MODEL=startModel();
    static volatile String selectedOrder;
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("function_chat").withUsername("test").withPassword("test").withReuse(false);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        if(!MYSQL.isRunning())MYSQL.start();
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);
        r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("merchant.agent.base-url",()->"http://127.0.0.1:"+MODEL.getAddress().getPort()+"/v1");
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired RagProperties rag;
    @MockitoBean SessionService sessions;
    @MockitoBean KnowledgeEmbedding embedding; @MockitoBean MilvusKnowledgeStore vectors;
    long merchant,document,chunk;
    @BeforeEach void prepare(){
        REQUESTS.clear();rag.setEnabled(true);
        merchant=tenant("M_3C_DEMO");
        document=jdbc.queryForObject("SELECT COALESCE(MAX(id),70000)+10 FROM knowledge_document",Long.class);chunk=document+1;
        jdbc.update("INSERT INTO knowledge_document VALUES (?,?,?,'READY',1,2000000000000101,UTC_TIMESTAMP(3))",document,merchant,"订单政策");
        jdbc.update("INSERT INTO knowledge_chunk VALUES (?,?,?,?,?,UTC_TIMESTAMP(3))",chunk,merchant,document,1,"订单以实时查询状态为准。");
        when(embedding.embed(anyList(),any())).thenReturn(List.of(List.of(1f,0f)));
    }
    @AfterAll static void stopModel(){MODEL.stop(0);}

    @ParameterizedTest
    @CsvSource({"CONSUMER,2000000000000201,DEV-2026-0901,SHIPPED,158.00,true",
            "CONSUMER,2000000000000201,DEV-2026-0901,CANCELLED,158.00,false",
            "MERCHANT_STAFF,2000000000000101,DEV-2026-0903,PENDING_SHIPMENT,45.00,true",
            "MERCHANT_STAFF,2000000000000101,DEV-2026-0903,COMPLETED,45.00,false"})
    void authorizedLookupCrossesRealProtocolSqlAndSse(AccountRole role,long account,String order,String state,String amount,boolean hit)throws Exception {
        selectedOrder=order;authenticate(account,merchant,role);
        when(vectors.search(eq(merchant),anyList(),any())).thenReturn(hit?List.of(new MilvusKnowledgeStore.Hit(chunk,document,.99f)):List.of());
        // 查询前改变真实业务状态，证明结果来自当前数据库而非 fixture 常量。
        jdbc.update("UPDATE trade_order SET status=? WHERE merchant_id=? AND order_no=?",state,merchant,order);
        try(var client=HttpClient.newHttpClient()) {
            String session=create(client),path=path(session),body=body();
            var response=send(client,"POST",path,body);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("event:start","event:token","event:done").doesNotContain("event:error","tool_call_id","integration-stub-only");
            assertThat(REQUESTS).hasSize(2);
            var first=REQUESTS.getFirst();assertThat(first.path("tools")).hasSize(1);
            assertThat(first.path("tools").get(0).path("function").path("name").asText()).isEqualTo("lookup_order");
            assertThat(first.path("messages").get(first.path("messages").size()-1).path("content").asText()).isEqualTo("查询当前订单");
            assertThat(first.toString().contains("订单以实时查询状态为准。")).isEqualTo(hit);
            var second=REQUESTS.getLast();assertThat(second.has("tools")).isFalse();
            var messages=second.path("messages");var tool=messages.get(messages.size()-1);
            assertThat(tool.path("role").asText()).isEqualTo("tool");assertThat(tool.path("tool_call_id").asText()).isEqualTo("call_integration");
            var detail=json.readTree(tool.path("content").asText());
            assertThat(detail.path("orderNo").asText()).isEqualTo(order);
            assertThat(detail.path("status").asText()).isEqualTo(state);
            assertThat(detail.path("totalAmount").decimalValue()).isEqualByComparingTo(amount);
            assertThat(detail.path("items")).isNotEmpty();
            assertThat(tool.toString()).doesNotContain("phone","address","accessToken");
            var done=event(response.body(),"done");var history=json.readTree(send(client,"GET",path,null).body()).path("data").path("items");
            var saved=history.get(1);
            assertThat(saved.path("content").asText()).isEqualTo("订单状态："+state);
            assertThat(saved.path("toolCalls")).isEqualTo(done.path("toolCalls"));
            assertThat(done.path("toolCalls")).isEqualTo(json.readTree("[{\"name\":\"lookup_order\",\"status\":\"SUCCEEDED\"}]"));
            assertThat(saved.path("sources")).isEqualTo(done.path("sources")).hasSize(hit?1:0);
            assertThat(history.get(0).path("toolCalls")).isEmpty();
            var stored=jdbc.queryForMap("SELECT content,status,sources_json,tool_calls_json FROM chat_message WHERE id=?",Long.parseLong(done.path("messageId").asText()));
            assertThat(stored.get("content")).isEqualTo(saved.path("content").asText());assertThat(stored.get("status")).isEqualTo("COMPLETED");
            assertThat(json.readTree((String)stored.get("tool_calls_json"))).isEqualTo(done.path("toolCalls"));
            assertThat(json.readTree((String)stored.get("sources_json"))).isEqualTo(done.path("sources"));
            assertThat(done.path("toolCalls").toString()).doesNotContain(order,"orderNo","arguments","totalAmount","items");
            var duplicate=send(client,"POST",path,body);
            assertThat(duplicate.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
            assertThat(json.readTree(duplicate.body()).path("data").path("runId")).isEqualTo(done.path("runId"));
            assertThat(REQUESTS).hasSize(2);verify(vectors).search(eq(merchant),anyList(),any());
        }
    }
    @ParameterizedTest
    @CsvSource({"2000000000000202,M_3C_DEMO,DEV-2026-0901",
            "2000000000000201,M_3C_DEMO,ONLY-OTHER-TENANT",
            "2000000000000201,M_3C_DEMO,DOES-NOT-EXIST"})
    void inaccessibleAndMissingOrdersNeverReachFinalModel(long account,String tenant,String order)throws Exception {
        selectedOrder=order;long scope=tenant(tenant);authenticate(account,scope,AccountRole.CONSUMER);
        jdbc.update("UPDATE trade_order SET order_no='ONLY-OTHER-TENANT' WHERE id=2000000000000304");
        when(vectors.search(eq(scope),anyList(),any())).thenReturn(List.of());
        try(var client=HttpClient.newHttpClient()) {
            String path=path(create(client));var response=send(client,"POST",path,body());
            assertThat(response.body()).contains("event:error").doesNotContain("event:done","event:token",order,"totalAmount","customerId","SHIPPED");
            assertThat(event(response.body(),"error").path("code").asText()).isEqualTo("DEPENDENCY_ERROR");
            assertThat(REQUESTS).hasSize(1);assertThat(REQUESTS.getFirst().toString()).doesNotContain("totalAmount","tool_call_id");
            var saved=json.readTree(send(client,"GET",path,null).body()).path("data").path("items").get(1);
            assertThat(saved.path("status").asText()).isEqualTo("FAILED");assertThat(saved.path("content").asText()).isEmpty();
            assertThat(saved.path("toolCalls")).isEmpty();assertThat(saved.path("sources")).isEmpty();
        }
    }
    void authenticate(long account,long merchant,AccountRole role){
        when(sessions.authenticate("integration-token")).thenReturn(new Actor(new AuthPrincipal(account,merchant,role),"integration-session"));
    }
    long tenant(String code){return jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code=?",Long.class,code);}
    String path(String session){return "/api/v1/chat/sessions/"+session+"/messages";}
    String body()throws Exception{return json.writeValueAsString(Map.of("content","查询当前订单","clientMessageId",UUID.randomUUID().toString()));}
    String create(HttpClient client)throws Exception {
        var response=send(client,"POST","/api/v1/chat/sessions","{}");assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).path("data").path("sessionId").asText();
    }
    HttpResponse<String> send(HttpClient client,String method,String path,String body)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("Authorization","Bearer integration-token").header("Content-Type","application/json");
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode event(String stream,String name)throws Exception {
        for(String block:stream.split("\n\n"))if(block.contains("event:"+name))
            for(String line:block.split("\n"))if(line.startsWith("data:"))return json.readTree(line.substring(5));
        throw new AssertionError("Missing event "+name);
    }
    static HttpServer startModel(){
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/chat/completions",http->{
                try {
                    var request=JSON.readTree(http.getRequestBody().readAllBytes());REQUESTS.add(request);
                    var messages=request.path("messages");String response;
                    if(messages.get(messages.size()-1).path("role").asText().equals("tool")) {
                        var detail=JSON.readTree(messages.get(messages.size()-1).path("content").asText());
                        response=delta(Map.of("content","订单状态："+detail.path("status").asText()))+finish("stop");
                    } else {
                        response=delta(Map.of("tool_calls",List.of(Map.of("index",0,"id","call_integration","type","function",
                                "function",Map.of("name","lookup_order","arguments",JSON.writeValueAsString(Map.of("orderNo",selectedOrder)))))))+finish("tool_calls");
                    }
                    var bytes=(response+"data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                    http.getResponseHeaders().set("Content-Type","text/event-stream");http.sendResponseHeaders(200,bytes.length);
                    http.getResponseBody().write(bytes);
                } finally {http.close();}
            });server.start();return server;
        } catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    static String delta(Map<String,Object> value)throws java.io.IOException {
        return "data: "+JSON.writeValueAsString(Map.of("choices",List.of(Map.of("index",0,"delta",value))))+"\n\n";
    }
    static String finish(String reason)throws java.io.IOException {
        return "data: "+JSON.writeValueAsString(Map.of("choices",List.of(Map.of("index",0,"delta",Map.of(),"finish_reason",reason))))+"\n\n";
    }
}
