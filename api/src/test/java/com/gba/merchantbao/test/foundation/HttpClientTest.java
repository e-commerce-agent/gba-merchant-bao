package com.gba.merchantbao.test.foundation;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.AccessTokenService;

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
        "merchant.websocket.enabled=false",
        // HTTP 契约不访问中间件；不可达地址防止继承开发者连接。
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/http_contract_no_database",
        "spring.datasource.username=unused",
        "spring.datasource.password=unused",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HttpClientTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.gba.merchantbao.legacy.service.EmployeeService employeeService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.gba.merchantbao.legacy.service.UserService userService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.gba.merchantbao.legacy.service.AddressBookService addressBookService;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private com.gba.merchantbao.legacy.properties.JwtProperties jwtProperties;
    @Autowired
    private com.gba.merchantbao.auth.service.AccessTokenService accessTokens;

    @org.junit.jupiter.api.AfterEach
    void clearLegacyTestContext() {
        com.gba.merchantbao.legacy.context.BaseContext.removeCurrentId();
    }

    @Test
    void legacyLoginPathsHeadersAndEnvelopeRemainCompatible() throws Exception {
        var employee = new com.gba.merchantbao.legacy.entity.Employee();
        employee.setId(101L); employee.setUsername("contract-staff"); employee.setName("Contract");
        org.mockito.Mockito.when(employeeService.login(org.mockito.ArgumentMatchers.any())).thenReturn(employee);
        var user = new com.gba.merchantbao.legacy.entity.User();
        user.setId(102L); user.setOpenid("test-openid");
        org.mockito.Mockito.when(userService.wxLogin(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        org.mockito.Mockito.when(addressBookService.list(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());

        String adminToken = loginToken("/admin/employee/login", "{\"username\":\"contract-staff\"}");
        String userToken = loginToken("/user/user/login", "{}");
        org.assertj.core.api.Assertions.assertThat(com.gba.merchantbao.legacy.utils.JwtUtil.parseJWT(
                jwtProperties.getAdminSecretKey(), adminToken)).containsKey("empId");
        org.assertj.core.api.Assertions.assertThat(com.gba.merchantbao.legacy.utils.JwtUtil.parseJWT(
                jwtProperties.getUserSecretKey(), userToken)).containsKey("userId");
        mockMvc.perform(post("/admin/employee/logout").header("token", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/user/addressBook/list").header("authentication", userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(post("/admin/employee/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/addressBook/list").header("token", userToken)).andExpect(status().isUnauthorized());
        for (var role : com.gba.merchantbao.auth.enums.AccountRole.values()) {
            var now = java.time.Instant.now();
            String newToken = accessTokens.issue(new com.gba.merchantbao.auth.security.AuthPrincipal(101, 202, role),
                    "sid", now, now.plusSeconds(86400)).token();
            mockMvc.perform(post("/admin/employee/logout").header("token", newToken)).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/user/addressBook/list").header("authentication", newToken)).andExpect(status().isUnauthorized());
        }
    }

    private String loginToken(String path, String body) throws Exception {
        var response = mockMvc.perform(post(path).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").hasJsonPath()).andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.message").doesNotExist()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

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
    void anonymousEchoUsesPublicUnauthorizedEnvelopeAndRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/echo")
                        .header("X-Request-Id", "test-request-123")
                        .contentType("application/json")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "test-request-123"))
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.requestId").value("test-request-123"))
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }
}
