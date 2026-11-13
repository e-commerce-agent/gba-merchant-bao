package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.entity.KnowledgeChunk;
import com.gba.merchantbao.knowledge.mapper.KnowledgeMapper;
import com.gba.merchantbao.knowledge.service.*;
import com.gba.merchantbao.test.support.DockerRequired;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockPart;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Testcontainers
@org.springframework.test.annotation.DirtiesContext
class KnowledgeIngestionIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34").withReuse(false);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl); r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired MockMvc http;
    @Autowired KnowledgeService service;
    @Autowired KnowledgeMapper mapper;
    @Autowired RagProperties properties;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SessionService sessions;
    @MockitoBean KnowledgeEmbedding embedding;
    @MockitoBean MilvusKnowledgeStore vectors;
    final Actor staff = actor(1000000000000001L, AccountRole.MERCHANT_STAFF);
    static Actor actor(long merchant, AccountRole role) { return new Actor(new AuthPrincipal(100, merchant, role), "test-session"); }
    @BeforeEach void setup() {
        properties.setEnabled(true);
        when(sessions.authenticate("staff")).thenReturn(staff);
        when(sessions.authenticate("consumer")).thenReturn(actor(1000000000000001L, AccountRole.CONSUMER));
        when(embedding.embed(anyList(), any())).thenReturn(List.of(List.of(1f, 0f, 0f)));
    }
    @Test void successfulHttpUploadIsPublishedOnlyAfterConfirmedVectorsAndTenantScopedPagination() throws Exception {
        doAnswer(call -> {
            long merchant = call.getArgument(0), document = call.getArgument(1);
            assertThat(jdbc.queryForObject("SELECT status FROM knowledge_document WHERE id=?", String.class, document)).isEqualTo("PROCESSING");
            assertThat(mapper.readyChunks(merchant, call.getArgument(2))).isEmpty();
            assertThat(mapper.readyReferences(merchant, call.getArgument(2))).isEmpty();
            return null;
        }).when(vectors).insertAndConfirm(anyLong(), anyLong(), anyList(), anyList(), any());
        var response = http.perform(multipart("/api/v1/knowledge/documents")
                        .part(new MockPart("title", "售后政策".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .part(new MockPart("file", "policy.md", "七日内按约定退货。".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.documentId").isString()).andReturn();
        long document = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("documentId").asLong();
        var ids = jdbc.queryForList("SELECT id FROM knowledge_chunk WHERE document_id=?", Long.class, document);
        assertThat(mapper.readyChunks(staff.principal().merchantId(), ids)).extracting(KnowledgeChunk::content).containsExactly("七日内按约定退货。");
        assertThat(mapper.readyChunks(1000000000000002L, ids)).isEmpty();
        assertThat(mapper.readyReferences(staff.principal().merchantId(), ids)).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("售后政策"); assertThat(c.content()).isEqualTo("七日内按约定退货。");
        });
        assertThat(mapper.readyReferences(1000000000000002L, ids)).isEmpty();
        var next = service.upload(staff, "另一个文件", KnowledgeTextTest.file("a.txt", "另一段内容"));
        var first = service.list(staff, 1, null);
        assertThat(first.hasMore()).isTrue(); assertThat(first.items().getFirst().documentId()).isEqualTo(next.documentId());
        assertThat(service.list(staff, 100, first.nextCursor()).items()).noneMatch(d -> d.documentId().equals(next.documentId()));
        assertThat(service.list(actor(1000000000000002L, AccountRole.MERCHANT_STAFF), 20, null).items()).isEmpty();
    }
    @Test void partialVectorFailureRemainsFailedAndUnavailableToReadyReads() {
        doThrow(new IllegalStateException("dependency details must stay private")).when(vectors)
                .insertAndConfirm(anyLong(), anyLong(), anyList(), anyList(), any());
        assertThatThrownBy(() -> service.upload(staff, "失败资料", KnowledgeTextTest.file("bad.txt", "不能作为证据")))
                .hasMessage("model dependency unavailable");
        var failed = service.list(staff, 100, null).items().stream().filter(d -> d.title().equals("失败资料")).findFirst().orElseThrow();
        assertThat(failed.status()).isEqualTo("FAILED");
        var ids = jdbc.queryForList("SELECT id FROM knowledge_chunk WHERE document_id=?", Long.class, failed.documentId().value());
        assertThat(ids).isNotEmpty(); assertThat(mapper.readyChunks(staff.principal().merchantId(), ids)).isEmpty();
        assertThat(mapper.readyReferences(staff.principal().merchantId(), ids)).isEmpty();
    }
    @Test void rolesPrecedeMultipartParsingAndInvalidFilesNeverReachEmbedding() throws Exception {
        for (String method : List.of("GET", "POST")) {
            http.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/v1/knowledge/documents")
                    .contentType("multipart/form-data; boundary=bad").content("broken multipart")
                    .header("Authorization", "Bearer consumer")).andExpect(status().isForbidden());
        }
        when(sessions.authenticate(null)).thenThrow(com.gba.merchantbao.auth.exception.AuthError.unauthorized());
        http.perform(post("/api/v1/knowledge/documents").contentType("multipart/form-data; boundary=bad").content("broken"))
                .andExpect(status().isUnauthorized());
        for (MockMultipartFile f : List.of(KnowledgeTextTest.file("a.txt", "  "), KnowledgeTextTest.file("a.pdf", "正文"),
                new MockMultipartFile("file", "a.md", "text/plain", new byte[]{(byte)0xff}))) {
            assertThatThrownBy(() -> service.upload(staff, "非法资料", f)).isInstanceOf(com.gba.merchantbao.knowledge.exception.KnowledgeError.class);
        }
        verifyNoInteractions(embedding, vectors);
        http.perform(get("/api/v1/knowledge/documents").header("Authorization", "Bearer staff").param("size", "101"))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/knowledge/documents").header("Authorization", "Bearer staff").param("merchantId", "2"))
                .andExpect(status().isBadRequest());
    }
}
