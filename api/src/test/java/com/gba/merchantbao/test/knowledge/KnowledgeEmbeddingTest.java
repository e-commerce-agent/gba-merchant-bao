package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class KnowledgeEmbeddingTest {
    @Test void usesIndependentEmbeddingProtocolAndRejectsWrongDimension() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var dimension = new java.util.concurrent.atomic.AtomicInteger(3);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        server.createContext("/v1/embeddings", exchange -> {
            try {
                var json = new ObjectMapper(); var request = json.readTree(exchange.getRequestBody());
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer embedding-only-test");
                assertThat(request.path("model").asText()).isEqualTo("test-embedding");
                assertThat(request.path("dimensions").asInt()).isEqualTo(3);
                assertThat(request.path("input").get(0).asText()).isEqualTo("政策资料");
                String response = "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":"
                        + (dimension.get() == 3 ? "[1,0,0]" : "[1,0]") + "}],\"model\":\"test-embedding\",\"usage\":{\"prompt_tokens\":3,\"total_tokens\":3}}";
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
            } catch (Throwable error) { failure.set(error); exchange.sendResponseHeaders(500, -1); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var p = new RagProperties(); p.setEmbeddingBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            p.setEmbeddingApiKey("embedding-only-test"); p.setEmbeddingModel("test-embedding"); p.setEmbeddingDimension(3);
            var embeddings = new KnowledgeEmbedding(p);
            assertThat(embeddings.embed(List.of("政策资料"), new ImportDeadline())).containsExactly(List.of(1f, 0f, 0f));
            dimension.set(2);
            assertThatThrownBy(() -> embeddings.embed(List.of("政策资料"), new ImportDeadline())).hasMessageContaining("dimension");
            assertThat(failure.get()).isNull();
        } finally { server.stop(0); }
    }
}
