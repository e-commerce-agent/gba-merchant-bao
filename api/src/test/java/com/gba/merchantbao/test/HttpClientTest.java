package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "merchant.websocket.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HttpClientTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void foundationHealthUsesVersionedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", not(blankOrNullString())))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void legacyUserRouteKeepsAuthenticationBoundary() throws Exception {
        mockMvc.perform(get("/user/shop/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", not(blankOrNullString())));
    }

    @Test
    void springdocEndpointsAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.operationId").value("getFoundationHealth"))
                .andExpect(jsonPath("$.paths['/api/v1/echo'].post.operationId").value("echoFoundationMessage"))
                .andExpect(jsonPath("$.paths['/user/shop/status'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void validationErrorsUsePublicEnvelopeAndRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/echo")
                        .header("X-Request-Id", "test-request-123")
                        .contentType("application/json")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "test-request-123"))
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.requestId").value("test-request-123"))
                .andExpect(jsonPath("$.message").value("invalid argument"));
    }
}
