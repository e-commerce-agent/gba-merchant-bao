package com.gba.merchantbao.test.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.*;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class AgentModelProtocolTest {
    @Test void sdkTransportCanBeCancelledBeforeTheFirstBodyToken() throws Exception {
        var accepted=new CountDownLatch(1);var release=new CountDownLatch(1);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type","text/event-stream");
            exchange.sendResponseHeaders(200,0);accepted.countDown();
            try {
                release.await(2,TimeUnit.SECONDS);
                exchange.getResponseBody().write(("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"late\"}}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            } catch(java.io.IOException ignored) { /* The cancelled client may have closed the socket. */ }
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            finally {exchange.close();}
        });server.start();
        try {
            var p=new AgentProperties();p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");p.setApiKey("stub-secret");p.setModel("stub-model");
            var result=new CompletableFuture<String>();
            var call=new OpenAiAgentModel(p).stream(List.of(UserMessage.from("cancel")),new AgentModel.Listener(){
                public void delta(String s){result.complete("delta");}public void complete(AgentModel.Usage u){result.complete("done");}public void error(Throwable e){result.complete("error");}
            });
            assertThat(accepted.await(2,TimeUnit.SECONDS)).isTrue();call.cancel();release.countDown();
            assertThatThrownBy(()->result.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } finally {release.countDown();server.stop(0);}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"Authorization","api-key"})
    void sdkSendsCompatibleRolesAndParsesFragmentedStream(String authHeader) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<String>(); var path = new AtomicReference<String>(); var bearer = new AtomicReference<String>();
        var headerKey = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath()); bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            headerKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            String stream = "data: {\"id\":\"s1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"你\"}}]}\n\n"
                    + "data: {\"id\":\"s1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"好\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
            for (int i=0; i<bytes.length; i+=7) { exchange.getResponseBody().write(bytes,i,Math.min(7,bytes.length-i)); exchange.getResponseBody().flush(); }
            exchange.close();
        });
        server.start();
        try {
            var p = new AgentProperties(); p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
            p.setApiKey("stub-secret"); p.setModel("stub-model");
            new org.springframework.boot.context.properties.bind.Binder(
                    new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                            java.util.Map.of("merchant.agent.api-key-header",authHeader)))
                    .bind("merchant.agent",org.springframework.boot.context.properties.bind.Bindable.ofInstance(p));
            var result = new CompletableFuture<String>(); var text = new StringBuilder();
            new OpenAiAgentModel(p).stream(List.of(SystemMessage.from("system"),UserMessage.from("old"),AiMessage.from("answer"),UserMessage.from("now")),new AgentModel.Listener(){
                public void delta(String s){ text.append(s); }
                public void complete(AgentModel.Usage usage){ assertThat(usage).isNull(); result.complete(text.toString()); }
                public void error(Throwable e){result.completeExceptionally(e);}
            });
            assertThat(result.get(10,TimeUnit.SECONDS)).isEqualTo("你好");
            assertThat(path.get()).isEqualTo("/v1/chat/completions");
            if(authHeader.equals("Authorization")){assertThat(bearer.get()).isEqualTo("Bearer stub-secret");assertThat(headerKey.get()).isNull();}
            else {assertThat(headerKey.get()).isEqualTo("stub-secret");assertThat(bearer.get()).isNull();}
            var json = new ObjectMapper().readTree(request.get());
            assertThat(json.path("model").asText()).isEqualTo("stub-model"); assertThat(json.path("stream").asBoolean()).isTrue();
            assertThat(json.path("max_tokens").asInt()).isEqualTo(2048);
            assertThat(json.path("messages").findValuesAsText("role")).containsExactly("system","user","assistant","user");
            assertThat(json.path("messages").findValuesAsText("content")).containsExactly("system","old","answer","now");
            assertThat(request.get()).doesNotContain("tools", "reasoning_content");
        } finally { server.stop(0); }
    }
}
