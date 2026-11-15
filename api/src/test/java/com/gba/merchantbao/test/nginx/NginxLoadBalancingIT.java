package com.gba.merchantbao.test.nginx;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.MerchantApplication;
import com.gba.merchantbao.agent.service.OrderLookupCall;
import com.gba.merchantbao.test.support.DockerRequired;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** No mocked application beans: real JWT/Redis/SQL, two Servlet contexts and the committed Nginx config. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(90)
class NginxLoadBalancingIT {
    final Path output = Path.of("target/nginx-load-balancing").toAbsolutePath();
    final ObjectMapper json = new ObjectMapper();
    final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("nginx_acceptance").withUsername("test").withPassword("test").withReuse(false);
    final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379).withReuse(false);
    final ServletWebServerApplicationContext[] nodes = new ServletWebServerApplicationContext[2];
    final int[] ports = new int[2];
    final Map<String, AtomicInteger> modelCalls = new ConcurrentHashMap<>();
    final Map<String, Integer> modelNode = new ConcurrentHashMap<>();
    final List<Map<String, Object>> evidence = new ArrayList<>();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final ExecutorService providerThreads = Executors.newCachedThreadPool();
    HttpServer provider;
    GenericContainer<?> nginx;
    JdbcTemplate jdbc;
    String proxy, token;

    @BeforeAll void start() throws Exception {
        DockerRequired.check();
        Files.createDirectories(output);
        mysql.start(); redis.start();
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.setExecutor(providerThreads);
        provider.createContext("/", this::model);
        provider.start();
        for (int i = 0; i < 2; i++) {
            // Command-line properties override application-test.yml; dev is never active.
            nodes[i] = (ServletWebServerApplicationContext) new SpringApplicationBuilder(MerchantApplication.class)
                    .registerShutdownHook(false).run(
                            "--spring.profiles.active=test", "--server.port=0", "--server.shutdown=immediate",
                            "--logging.file.path=" + output.resolve("node-" + i),
                            "--spring.datasource.url=" + mysql.getJdbcUrl(),
                            "--spring.datasource.username=" + mysql.getUsername(), "--spring.datasource.password=" + mysql.getPassword(),
                            "--spring.data.redis.host=" + redis.getHost(), "--spring.data.redis.port=" + redis.getMappedPort(6379),
                            "--merchant.id.worker-id=" + (1000 + i), "--merchant.rag.enabled=false",
                            "--merchant.agent.enabled=true", "--merchant.agent.api-key=nginx-test-only",
                            "--merchant.agent.model=nginx-protocol-stub", "--merchant.agent.max-retries=0",
                            "--merchant.agent.base-url=http://127.0.0.1:" + provider.getAddress().getPort() + "/node-" + i + "/v1",
                            "--merchant.agent.heartbeat=100ms", "--merchant.agent.first-token-timeout=5s",
                            "--merchant.agent.total-timeout=25s", "--merchant.auth.namespace=nginx-acceptance",
                            "--merchant.auth.limits.login-ip-per-window=100");
            ports[i] = nodes[i].getWebServer().getPort();
        }
        jdbc = nodes[0].getBean(JdbcTemplate.class);
        new ResourceDatabasePopulator(new ClassPathResource("db/fixture/auth_accounts.sql"))
                .execute(nodes[0].getBean(DataSource.class));
        jdbc.update("""
                INSERT INTO trade_order (id,merchant_id,order_no,customer_id,status,pay_status,total_amount,currency)
                SELECT 2000000000000901,id,'NGINX-ORDER',2000000000000201,'PENDING_PAYMENT','UNPAID',1.00,'CNY'
                FROM merchant WHERE merchant_code='M_3C_DEMO'
                """);
        Testcontainers.exposeHostPorts(ports);
        String config = Files.readString(Path.of("../docker/nginx/dev.conf"));
        for (int i = 0; i < 2; i++) config = config.replace("host.docker.internal:" + (i == 0 ? 8080 : 8082),
                "host.testcontainers.internal:" + ports[i]);
        Path generated = output.resolve("nginx.conf");
        Files.writeString(generated, config);
        nginx = new GenericContainer<>("nginx:1.28.0-alpine").withExposedPorts(80).withReuse(false)
                .withCopyFileToContainer(MountableFile.forHostPath(generated), "/etc/nginx/conf.d/default.conf")
                .waitingFor(Wait.forHttp("/api/v1/health").forStatusCode(200));
        nginx.start();
        assertThat(nginx.execInContainer("nginx", "-t").getExitCode()).isZero();
        proxy = "http://" + nginx.getHost() + ":" + nginx.getMappedPort(80);
        token = data(call(proxy, null, "POST", "/api/v1/auth/staff/login", json.writeValueAsString(Map.of(
                "merchantCode", "M_3C_DEMO", "identifier", "fixture_staff", "password", "FixtureOnly-Auth-2026!")), "login", 200))
                .path("accessToken").asText();
        assertThat(token).isNotBlank();
    }

    @AfterAll void stop() throws Exception {
        try {
            if (nginx != null && nginx.isRunning()) Files.writeString(output.resolve("access.log"), nginx.getLogs());
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("results.json").toFile(), evidence);
        } finally {
            if (nginx != null) nginx.close();
            for (var node : nodes) if (node != null && node.isActive()) node.close();
            if (provider != null) provider.stop(0);
            providerThreads.shutdownNow(); client.close(); redis.close(); mysql.close();
        }
    }

    @Test @Order(1) void roundRobinSharedLoginHistoryAndProgressiveSse() throws Exception {
        for (int i = 0; i < 12; i++) call(proxy, token, "GET", "/api/v1/chat/sessions?size=10",
                null, "read-" + i, 200);
        call(proxy, null, "GET", "/api/v1/health?secret=must-not-log", null, "query-privacy", 200);
        Set<String> addresses = new HashSet<>();
        for (int i = 0; i < 12; i++) addresses.add(log("read-" + i).path("upstream_addr").asText());
        assertThat(addresses).hasSize(2);
        assertThat(nginx.getLogs()).doesNotContain("must-not-log", token, "FixtureOnly-Auth-2026!");
        for (int port : ports) call("http://127.0.0.1:" + port, token, "GET", "/api/v1/chat/sessions", null, "direct", 200);

        String session = create(), key = UUID.randomUUID().toString(), body = body("spaced-success", key);
        Trace trace = stream(session, body, "success");
        assertThat(trace.events()).containsExactly("start", "token", "token", "token", "done");
        List<Long> tokens = trace.times("token");
        assertThat(tokens.get(1) - tokens.get(0)).isGreaterThan(1000);
        assertThat(tokens.get(2) - tokens.get(1)).isGreaterThan(1000);
        assertThat(trace.times("done").getFirst() - tokens.getLast()).isGreaterThan(1000);
        assertThat(trace.times("done").getFirst()).isGreaterThan(5500);
        JsonNode assistant = stored(session, "COMPLETED", "one two three");
        var proxyRetry = call(proxy, token, "POST", messages(session), body, "success-retry", 200);
        assertThat(proxyRetry.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(data(proxyRetry).path("runId")).isEqualTo(assistant.path("runId"));
        // Retry deliberately on the node that did not execute the original POST.
        int other = 1 - modelNode.get("spaced-success");
        var retry = call("http://127.0.0.1:" + ports[other], token, "POST", messages(session), body, "retry-other", 200);
        assertThat(retry.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(data(retry).path("runId")).isEqualTo(assistant.path("runId"));
        call(proxy, token, "POST", messages(session), body("different", key), "conflict", 409);
        assertThat(calls("spaced-success")).isOne();
        assertSingleUpstream("success");
        evidence.add(Map.of("case", "shared-login-round-robin-and-success", "upstreams", addresses, "trace", trace.frames,
                "modelCalls", calls("spaced-success"), "history", "COMPLETED", "crossNodeJsonRetry", true));
    }

    @Test @Order(2) void controlledProviderErrorProducesStartErrorAndQueryableFailedHistory() throws Exception {
        String session = create(), body = body("controlled-error", UUID.randomUUID().toString());
        Trace trace = stream(session, body, "failure");
        assertThat(trace.events()).containsExactly("start", "error");
        stored(session, "FAILED", "");
        var retry = call(proxy, token, "POST", messages(session), body, "failure-retry", 200);
        assertThat(data(retry).path("status").asText()).isEqualTo("FAILED");
        assertThat(calls("controlled-error")).isOne();
        assertSingleUpstream("failure");
        evidence.add(Map.of("case", "controlled-error", "trace", trace.frames, "history", "FAILED", "modelCalls", 1));
    }

    @Test @Order(3) void clientDisconnectCancelsWithoutReplayingPost() throws Exception {
        String session = create(), body = body("client-disconnect", UUID.randomUUID().toString());
        var response = client.send(request(proxy, token, "POST", messages(session), body, "disconnect"), HttpResponse.BodyHandlers.ofInputStream());
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            while (true) { String line = reader.readLine(); assertThat(line).isNotNull(); if (line.equals("event:token")) break; }
        }
        awaitTerminal(session, "CANCELLED");
        stored(session, "CANCELLED", "partial");
        assertThat(data(call(proxy, token, "POST", messages(session), body, "disconnect-retry", 200)).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(calls("client-disconnect")).isOne();
        assertSingleUpstream("disconnect");
        evidence.add(Map.of("case", "client-disconnect", "history", "CANCELLED", "modelCalls", 1));
    }

    @Test @Order(4) void backendInterruptionDoesNotReplayEstablishedPostOnSurvivor() throws Exception {
        String session = create(), body = body("backend-interrupt", UUID.randomUUID().toString());
        var response = client.send(request(proxy, token, "POST", messages(session), body, "interrupt"), HttpResponse.BodyHandlers.ofInputStream());
        var received = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) { received.append(line).append('\n'); if (line.equals("event:token")) break; }
            assertThat(received.toString()).contains("event:start", "event:token");
            nodes[modelNode.get("backend-interrupt")].close();
            try { while ((line = reader.readLine()) != null) received.append(line).append('\n'); }
            catch (IOException expected) { /* Nginx terminates the established stream; it cannot resume it. */ }
        }
        assertThat(received.toString()).doesNotContain("event:done");
        // The surviving node sees the persisted cancellation and returns the same run as JSON.
        int survivor = 1 - modelNode.get("backend-interrupt");
        String direct = "http://127.0.0.1:" + ports[survivor];
        var history = data(call(direct, token, "GET", messages(session), null, "survivor-history", 200)).path("items").get(1);
        assertThat(history.path("status").asText()).isEqualTo("CANCELLED");
        var retry = call(direct, token, "POST", messages(session), body, "interrupt-retry", 200);
        assertThat(retry.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(data(retry).path("runId")).isEqualTo(history.path("runId"));
        assertThat(calls("backend-interrupt")).isOne();
        assertSingleUpstream("interrupt");
        evidence.add(Map.of("case", "backend-interrupt", "history", "CANCELLED", "modelCalls", 1,
                "upstream", log("interrupt").path("upstream_addr").asText(), "doneReceived", false));
    }

    void model(HttpExchange exchange) throws IOException {
        try {
            JsonNode request = json.readTree(exchange.getRequestBody());
            String prompt = "";
            for (var message : request.path("messages")) if (message.path("role").asText().equals("user")) prompt = message.path("content").asText();
            boolean selection = request.has("tools") && !prompt.equals("controlled-error");
            modelCalls.computeIfAbsent(prompt + (selection ? "/selection" : ""), k -> new AtomicInteger()).incrementAndGet();
            modelNode.put(prompt, exchange.getRequestURI().getPath().contains("node-0") ? 0 : 1);
            if (prompt.equals("controlled-error")) {
                byte[] error = "{\"error\":{\"message\":\"controlled failure\",\"type\":\"server_error\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(503, error.length);
                exchange.getResponseBody().write(error); return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            if (selection) {
                // Tool selection intentionally buffers text in the application. Query the isolated
                // fixture order, then stream the final answer with tools disabled.
                String tool = json.writeValueAsString(Map.of("choices", List.of(Map.of("index", 0, "delta", Map.of(
                        "tool_calls", List.of(Map.of("index", 0, "id", "nginx-lookup", "type", "function", "function",
                                Map.of("name", OrderLookupCall.NAME, "arguments", "{\"orderNo\":\"NGINX-ORDER\"}"))))))));
                exchange.getResponseBody().write(("data: " + tool + "\n\ndata: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush(); return;
            }
            if (prompt.equals("spaced-success")) {
                for (String text : List.of("one", " two", " three")) { chunk(exchange, text); Thread.sleep(2000); }
                exchange.getResponseBody().write(("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            } else {
                chunk(exchange, "partial");
                // Keep the model stream open until cancellation/teardown; heartbeats expose client disconnects.
                for (int i = 0; i < 100; i++) { Thread.sleep(200); exchange.getResponseBody().write(": waiting\n\n".getBytes(StandardCharsets.UTF_8)); exchange.getResponseBody().flush(); }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException expectedDisconnect) { /* Controlled client/backend cancellation closes model transport. */ }
        finally { exchange.close(); }
    }

    void chunk(HttpExchange exchange, String text) throws IOException {
        String chunk = json.writeValueAsString(Map.of("id", "nginx-stub", "choices", List.of(Map.of("index", 0, "delta", Map.of("content", text)))));
        exchange.getResponseBody().write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8)); exchange.getResponseBody().flush();
    }
    int calls(String prompt) {
        int generations = modelCalls.getOrDefault(prompt, new AtomicInteger()).get();
        if (!prompt.equals("controlled-error"))
            assertThat(modelCalls.getOrDefault(prompt + "/selection", new AtomicInteger()).get()).as("selection requests").isOne();
        return generations;
    }
    String messages(String id) { return "/api/v1/chat/sessions/" + id + "/messages"; }
    String body(String content, String key) throws Exception { return json.writeValueAsString(Map.of("content", content, "clientMessageId", key)); }
    String create() throws Exception { return data(call(proxy, token, "POST", "/api/v1/chat/sessions", "{}", "create-" + UUID.randomUUID(), 201)).path("sessionId").asText(); }
    JsonNode data(HttpResponse<String> response) throws Exception { return json.readTree(response.body()).path("data"); }
    HttpRequest request(String base, String bearer, String method, String path, String body, String id) {
        var b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(35)).header("X-Request-Id", id);
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        if (body != null) b.header("Content-Type", "application/json");
        return b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    HttpResponse<String> call(String base, String bearer, String method, String path, String body, String id, int status) throws Exception {
        var response = client.send(request(base, bearer, method, path, body, id), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("X-Request-Id")).hasValue(id);
        return response;
    }
    record Frame(String event, long elapsedMs) { }
    record Trace(List<Frame> frames) {
        List<String> events() { return frames.stream().map(Frame::event).toList(); }
        List<Long> times(String event) { return frames.stream().filter(f -> f.event.equals(event)).map(Frame::elapsedMs).toList(); }
    }
    Trace stream(String session, String body, String id) throws Exception {
        long began = System.nanoTime();
        var response = client.send(request(proxy, token, "POST", messages(session), body, id), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        var frames = new ArrayList<Frame>();
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) if (line.startsWith("event:"))
                frames.add(new Frame(line.substring(6).strip(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began)));
        }
        return new Trace(frames);
    }
    JsonNode stored(String session, String state, String content) throws Exception {
        JsonNode first = null;
        for (int port : ports) {
            var assistant = data(call("http://127.0.0.1:" + port, token, "GET", messages(session), null, "history", 200)).path("items").get(1);
            assertThat(assistant.path("status").asText()).isEqualTo(state);
            assertThat(assistant.path("content").asText()).isEqualTo(content);
            if (first != null) assertThat(assistant).isEqualTo(first);
            first = assistant;
        }
        assertThat(jdbc.queryForObject("SELECT active_run_id FROM chat_session WHERE id=?", Long.class, session)).isNull();
        return first;
    }
    void awaitTerminal(String session, String state) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < until) {
            if (state.equals(jdbc.queryForObject("SELECT status FROM chat_message WHERE session_id=? AND sender_type='ASSISTANT'", String.class, session))) return;
            Thread.sleep(100);
        }
        fail("No %s state after disconnect", state);
    }
    JsonNode log(String id) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            for (String line : nginx.getLogs().lines().filter(l -> l.startsWith("{")).toList()) {
                var row = json.readTree(line); if (row.path("request_id").asText().equals(id)) return row;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Missing Nginx access log: " + id);
    }
    void assertSingleUpstream(String id) throws Exception {
        assertThat(log(id).path("upstream_addr").asText()).isNotBlank().doesNotContain(",");
    }
}
