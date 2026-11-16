package com.gba.merchantbao.test.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.*;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class AgentFunctionCallingProtocolTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET = "protocol-stub-secret";
    private static final String ORDER_NO = "ORDER_private-42";
    private static final String ARGUMENTS = "{\"orderNo\":\"" + ORDER_NO + "\"}";

    @Test void declaresOneToolAndPreservesAssistantAndToolMessagesForFinalText(CapturedOutput output) throws Exception {
        String selection = delta(Map.of("content", "先查询订单"))
                + tool(0, "call_", "lookup_", "{\"order")
                + tool(0, "42", "order", "No\":\"" + ORDER_NO + "\"}")
                + finish("tool_calls")
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":8,\"total_tokens\":38}}\n\n"
                + done();
        try (var stub = new Stub(selection, text("已", "发货"))) {
            var model = stub.model();
            var messages = List.<ChatMessage>of(SystemMessage.from("system"), UserMessage.from("查订单"));
            var first = exchange(model, messages, true);
            assertThat(first.error).isNull();
            assertThat(first.text).isEmpty();
            assertThat(first.lookup.orderNo()).isEqualTo(ORDER_NO);
            assertThat(first.lookup.toString()).doesNotContain(ORDER_NO, ARGUMENTS);
            assertThat(first.usage).isEqualTo(new AgentModel.Usage(30L, 8L));
            assertThat(first.terminals.get()).isEqualTo(1);

            var request = stub.requests.poll(2, TimeUnit.SECONDS);
            var tools = request.path("tools");
            assertThat(tools.size()).isEqualTo(1);
            assertThat(tools.get(0).path("type").asText()).isEqualTo("function");
            var function = tools.get(0).path("function");
            assertThat(function.path("name").asText()).isEqualTo("lookup_order");
            assertThat(function.path("strict").asBoolean()).isTrue();
            assertThat(function.path("parameters").path("type").asText()).isEqualTo("object");
            assertThat(function.path("parameters").path("properties").size()).isEqualTo(1);
            assertThat(function.path("parameters").path("properties").path("orderNo").path("type").asText()).isEqualTo("string");
            assertThat(function.path("parameters").path("required").get(0).asText()).isEqualTo("orderNo");
            assertThat(function.path("parameters").path("additionalProperties").asBoolean(true)).isFalse();
            assertThat(request.path("parallel_tool_calls").asBoolean(true)).isFalse();

            var followUp = new ArrayList<>(messages);
            followUp.add(first.lookup.assistantMessage());
            followUp.add(first.lookup.toolResult("{\"status\":\"SHIPPED\"}"));
            var last = exchange(model, followUp, false);
            assertThat(last.error).isNull();
            assertThat(last.lookup).isNull();
            assertThat(last.text.toString()).isEqualTo("已发货");
            assertThat(last.deltas).containsExactly("已", "发货");
            assertThat(last.terminals.get()).isEqualTo(1);
            var second = stub.requests.poll(2, TimeUnit.SECONDS);
            assertThat(second.has("tools")).isFalse();
            assertThat(second.path("messages").findValuesAsText("role")).containsExactly("system", "user", "assistant", "tool");
            var assistant = second.path("messages").get(2);
            assertThat(assistant.path("content").asText()).isEqualTo("先查询订单");
            var call = assistant.path("tool_calls").get(0);
            assertThat(call.path("id").asText()).isEqualTo("call_42");
            assertThat(call.path("function").path("name").asText()).isEqualTo("lookup_order");
            assertThat(JSON.readTree(call.path("function").path("arguments").asText())).isEqualTo(JSON.readTree(ARGUMENTS));
            var result = second.path("messages").get(3);
            assertThat(result.path("tool_call_id").asText()).isEqualTo("call_42");
            assertThat(result.path("content").asText()).isEqualTo("{\"status\":\"SHIPPED\"}");
            assertPrivate(output);
        }
    }

    @Test void toolEnabledRoundCanCompleteWithoutLookup(CapturedOutput output) throws Exception {
        try (var stub = new Stub(text("你", "好"))) {
            var result = exchange(stub.model(), List.of(UserMessage.from("你好")), true);
            assertThat(result.error).isNull();
            assertThat(result.lookup).isNull();
            assertThat(result.text.toString()).isEqualTo("你好");
            assertThat(result.usage).isNull();
            assertThat(result.terminals.get()).isEqualTo(1);
            assertPrivate(output);
        }
    }

    static Stream<String> malformedArguments() {
        return Stream.of("", "{", "null", "[]", "\"text\"", "{}", "{\"orderNo\":null}",
                "{\"orderNo\":42}", "{\"orderNo\":\"\"}", "{\"orderNo\":\" bad\"}",
                "{\"orderNo\":\"a/b\"}", "{\"orderNo\":\"订单\"}",
                "{\"orderNo\":\"" + "A".repeat(65) + "\"}",
                ARGUMENTS + "{}",
                "{\"orderNo\":\"first\",\"orderNo\":\"" + ORDER_NO + "\"}",
                "{\"orderNo\":\"" + ORDER_NO + "\",\"merchantId\":1}",
                "{\"orderNo\":\"" + ORDER_NO + "\",\"accountId\":1}",
                "{\"orderNo\":\"" + ORDER_NO + "\",\"role\":\"STAFF\"}",
                "{\"orderNo\":\"" + "A".repeat(1100) + "\"}");
    }

    @ParameterizedTest @MethodSource("malformedArguments")
    void rejectsMalformedOrIdentityOverridingArguments(String arguments, CapturedOutput output) throws Exception {
        try (var stub = new Stub(tool(0, "call_1", "lookup_order", arguments) + finish("tool_calls") + done())) {
            assertRejected(exchange(stub.model(), List.of(UserMessage.from("查订单")), true));
            assertPrivate(output);
        }
    }

    static Stream<String> invalidResponses() {
        return Stream.of(
                tool(0, "call_1", "unknown_tool", ARGUMENTS) + finish("tool_calls") + done(),
                tool(0, "", "lookup_order", ARGUMENTS) + finish("tool_calls") + done(),
                tool(0, "call_1", "lookup_order", ARGUMENTS) + finish("length") + done(),
                tool(0, "call_1", "lookup_order", ARGUMENTS) + finish("stop") + done(),
                tool(0, "call_1", "lookup_order", ARGUMENTS) + done(),
                delta(Map.of("content", "internal")) + finish("tool_calls") + done(),
                tool(0, "call_1", "lookup_order", ARGUMENTS) + tool(1, "call_2", "lookup_order", ARGUMENTS) + finish("tool_calls") + done(),
                delta(Map.of("tool_calls", List.of(
                        toolObject(0, "call_1", "lookup_order", ARGUMENTS),
                        toolObject(1, "call_2", "lookup_order", ARGUMENTS)))) + finish("tool_calls") + done(),
                "data: {\"choices\":invalid,\"private\":\"" + ORDER_NO + "\"}\n\n" + done(),
                "event: error\ndata: " + SECRET + " " + ARGUMENTS + "\n\n" + done());
    }

    @ParameterizedTest @MethodSource("invalidResponses")
    void rejectsProtocolErrorsWithOneSanitizedTerminal(String response, CapturedOutput output) throws Exception {
        try (var stub = new Stub(response)) {
            assertRejected(exchange(stub.model(), List.of(UserMessage.from("查订单")), true));
            assertPrivate(output);
        }
    }

    @Test void toolDisabledRoundRejectsUnexpectedLookup(CapturedOutput output) throws Exception {
        try (var stub = new Stub(tool(0, "call_1", "lookup_order", ARGUMENTS) + finish("tool_calls") + done())) {
            assertRejected(exchange(stub.model(), List.of(UserMessage.from("最终回答")), false));
            assertPrivate(output);
        }
    }

    @Test void selectionTextHasConfiguredBound() throws Exception {
        try (var stub = new Stub(text("A".repeat(16001)))) {
            assertRejected(exchange(stub.model(), List.of(UserMessage.from("回答")), true));
        }
    }

    @Test void cancellationDuringToolArgumentsSuppressesLateCompletion() throws Exception {
        var accepted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", http -> {
            http.getRequestBody().readAllBytes();
            http.getResponseHeaders().set("Content-Type", "text/event-stream");
            http.sendResponseHeaders(200, 0);
            try {
                http.getResponseBody().write(tool(0, "call_1", "lookup_order", "{").getBytes(StandardCharsets.UTF_8));
                http.getResponseBody().flush();
                accepted.countDown();
                release.await(3, TimeUnit.SECONDS);
                http.getResponseBody().write((finish("tool_calls") + done()).getBytes(StandardCharsets.UTF_8));
            } catch (java.io.IOException ignored) {
                // Cancellation may close the response before the delayed chunk.
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { http.close(); }
        });
        server.start();
        try {
            var result = new Outcome();
            var call = model(server).stream(List.of(UserMessage.from("取消")), true, result);
            assertThat(accepted.await(3, TimeUnit.SECONDS)).isTrue();
            call.cancel();
            release.countDown();
            assertThatThrownBy(() -> result.finished.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(result.text).isEmpty();
        } finally { release.countDown(); server.stop(0); }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 429, 503})
    void httpFailuresKeepStatusWithoutSupplierBody(int status, CapturedOutput output) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", http -> {
            http.getRequestBody().readAllBytes();
            byte[] body = JSON.writeValueAsBytes(Map.of("error", Map.of("message", SECRET + " " + ARGUMENTS)));
            http.getResponseHeaders().set("Content-Type", "application/json");
            http.sendResponseHeaders(status, body.length);
            http.getResponseBody().write(body);
            http.close();
        });
        server.start();
        try {
            var outcome = exchange(model(server), List.of(UserMessage.from("查询")), true);
            assertThat(outcome.error).isInstanceOf(dev.langchain4j.exception.HttpException.class).hasNoCause();
            assertThat(((dev.langchain4j.exception.HttpException) outcome.error).statusCode()).isEqualTo(status);
            assertThat(outcome.error.getMessage()).doesNotContain(SECRET, ORDER_NO, ARGUMENTS);
            assertThat(outcome.terminals.get()).isEqualTo(1);
            assertPrivate(output);
        } finally { server.stop(0); }
    }
    private static void assertRejected(Outcome result) {
        assertThat(result.error).isExactlyInstanceOf(AgentModel.ProtocolException.class)
                .hasMessage("Invalid Agent model protocol").hasNoCause();
        assertThat(result.lookup).isNull();
        assertThat(result.text).isEmpty();
        assertThat(result.terminals.get()).isEqualTo(1);
    }

    private static void assertPrivate(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(SECRET, ORDER_NO, ARGUMENTS);
    }

    private static Outcome exchange(AgentModel model, List<ChatMessage> messages, boolean tools) throws Exception {
        var outcome = new Outcome();
        model.stream(messages, tools, outcome);
        return outcome.finished.get(10, TimeUnit.SECONDS);
    }

    private static class Outcome implements AgentModel.Listener {
        final CompletableFuture<Outcome> finished = new CompletableFuture<>();
        final StringBuilder text = new StringBuilder();
        final List<String> deltas = new ArrayList<>();
        final AtomicInteger terminals = new AtomicInteger();
        OrderLookupCall lookup;
        AgentModel.Usage usage;
        Throwable error;
        public void delta(String value) { text.append(value); deltas.add(value); }
        public void complete(AgentModel.Usage value) { usage = value; end(); }
        public void toolCall(OrderLookupCall value, AgentModel.Usage tokens) { lookup = value; usage = tokens; end(); }
        public void error(Throwable value) { error = value; end(); }
        private void end() { terminals.incrementAndGet(); finished.complete(this); }
    }

    private static OpenAiAgentModel model(HttpServer server) {
        var properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setApiKey(SECRET);
        properties.setModel("protocol-stub");
        return new OpenAiAgentModel(properties);
    }

    private static class Stub implements AutoCloseable {
        final HttpServer server;
        final BlockingQueue<JsonNode> requests = new LinkedBlockingQueue<>();
        Stub(String... streams) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var index = new AtomicInteger();
            server.createContext("/", http -> {
                requests.add(JSON.readTree(http.getRequestBody().readAllBytes()));
                http.getResponseHeaders().set("Content-Type", "text/event-stream");
                http.sendResponseHeaders(200, 0);
                try {
                    byte[] bytes = streams[index.getAndIncrement()].getBytes(StandardCharsets.UTF_8);
                    for (int i = 0; i < bytes.length; i += 7) {
                        http.getResponseBody().write(bytes, i, Math.min(7, bytes.length - i));
                        http.getResponseBody().flush();
                    }
                } catch (java.io.IOException ignored) {
                    // A rejected or cancelled protocol closes its transport immediately.
                } finally { http.close(); }
            });
            server.start();
        }
        OpenAiAgentModel model() { return AgentFunctionCallingProtocolTest.model(server); }
        public void close() { server.stop(0); }
    }

    private static String tool(int index, String id, String name, String arguments) {
        return delta(Map.of("tool_calls", List.of(toolObject(index, id, name, arguments))));
    }
    private static Map<String, Object> toolObject(int index, String id, String name, String arguments) {
        return Map.of("index", index, "id", id, "type", "function", "function", Map.of("name", name, "arguments", arguments));
    }
    private static String delta(Map<String, ?> delta) {
        return event(Map.of("choices", List.of(Map.of("index", 0, "delta", delta))));
    }
    private static String finish(String reason) {
        return event(Map.of("choices", List.of(Map.of("index", 0, "delta", Map.of(), "finish_reason", reason))));
    }
    private static String text(String... values) {
        return Stream.of(values).map(value -> delta(Map.of("content", value))).reduce("", String::concat) + finish("stop") + done();
    }
    private static String event(Object value) {
        try { return "data: " + JSON.writeValueAsString(value) + "\n\n"; }
        catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    private static String done() { return "data: [DONE]\n\n"; }
}
