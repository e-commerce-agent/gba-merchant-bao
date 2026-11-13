package com.gba.merchantbao.test.chat;

import com.gba.merchantbao.agent.service.AgentModel;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

/** Explicit opt-in only; not discovered by Surefire/Failsafe defaults or packaged in the application. */
@TestPropertySource(properties={"server.address=127.0.0.1", "merchant.agent.first-token-timeout=10s",
    "merchant.agent.total-timeout=30s"})
class AgentApifoxWorkspace extends ChatAcceptanceIT {
    @Test void serveApifoxOnDisposableInfrastructure() throws Exception {
        var pending = new java.util.concurrent.ConcurrentHashMap<String, AgentModel.Listener>();
        doAnswer(invocation -> {
            List<dev.langchain4j.data.message.ChatMessage> context = invocation.getArgument(0);
            AgentModel.Listener listener = invocation.getArgument(1);
            String command = ((UserMessage)context.getLast()).singleText();
            calls.incrementAndGet();
            if (command.equals("accept:timeout")) return (AgentModel.Call) () -> {};
            listener.delta("hello");
            if (command.equals("accept:abort") || command.equals("accept:busy")) {
                pending.put(command, listener);
                return (AgentModel.Call) () -> {};
            }
            if (command.equals("accept:error")) listener.error(new IllegalStateException("controlled upstream error"));
            else { listener.delta(" world"); listener.complete(null); }
            return (AgentModel.Call) () -> {};
        }).when(model).stream(anyList(), any());

        String owner = consumer("M_3C_DEMO", "+8613900000591");
        String staff = staff("M_3C_DEMO"), other = staff("M_ISOLATION_TEST");
        String session = create(owner), busy = create(owner), abort = create(owner);
        String controlKey = UUID.randomUUID().toString();
        var credentials = Map.of("baseUrl", "http://127.0.0.1:" + port, "accessToken", owner,
            "staffToken", staff, "otherTenantToken", other, "sessionId", session,
            "busySessionId", busy, "abortSessionId", abort);
        Path runtime = Path.of(".runtime", "manual", "agent"); Files.createDirectories(runtime);
        Path stop = runtime.resolve("agent-apifox.stop");
        if (Files.exists(stop)) throw new IllegalStateException("Previous workspace stop marker exists");
        var executor = Executors.newCachedThreadPool();
        var helper = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        helper.setExecutor(executor);
        helper.createContext("/fixture/" + controlKey, exchange -> {
            try {
                Map<String, ?> result = credentials;
                String query = exchange.getRequestURI().getRawQuery();
                if (query != null && query.startsWith("sessionId=")) {
                    String id = query.substring("sessionId=".length());
                    if (!id.matches("[1-9][0-9]*")) throw new IllegalArgumentException("sessionId");
                    result = Map.of("messages", jdbc.queryForList("SELECT CAST(run_id AS CHAR) AS run_id,sender_type,status,content,error_code "
                        + "FROM chat_message WHERE session_id=? ORDER BY message_seq", id),
                        "session", jdbc.queryForMap("SELECT status,active_run_id FROM chat_session WHERE id=?", id),
                        "modelCalls", calls.get());
                }
                byte[] bytes = json.writeValueAsBytes(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        helper.start();
        String fixtureUrl = "http://127.0.0.1:" + helper.getAddress().getPort() + "/fixture/" + controlKey;
        Files.writeString(runtime.resolve("agent-apifox-local.json"), json.writeValueAsString(Map.of(
            "fixtureUrl", fixtureUrl, "baseUrl", credentials.get("baseUrl"), "sessionId", session,
            "busySessionId", busy, "abortSessionId", abort)));
        System.out.println("APIFOX_WORKSPACE_READY baseUrl=" + credentials.get("baseUrl") + " fixtureUrl=" + fixtureUrl);
        Instant end = Instant.now().plus(Duration.ofMinutes(45));
        try {
            while (!Files.exists(stop) && Instant.now().isBefore(end)) Thread.sleep(250);
            if (!Files.exists(stop)) throw new IllegalStateException("Manual workspace timed out; no acceptance inferred");
        } finally {
            helper.stop(0); executor.shutdownNow();
            for (String token : List.of(owner, staff, other)) call(token, "POST", "/api/v1/auth/logout", "{}", 200);
            // Late provider results must not change a terminal observed by Apifox.
            pending.values().forEach(listener -> { listener.delta("late"); listener.complete(null); });
            Files.deleteIfExists(runtime.resolve("agent-apifox-local.json"));
        }
    }
}
