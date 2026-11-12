package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.security.AuthSecurityFilter;
import com.gba.merchantbao.auth.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicCatalogSecurityTest {
    @Test void publicCatalogIgnoresCredentialsWithoutAuthenticatingOrTouchingSessions() throws Exception {
        var sessions = mock(SessionService.class);
        var filter = new AuthSecurityFilter(sessions, null, null, null, null);
        for (String suffix : List.of("products", "products/123", "skus/456")) {
            for (String token : List.of("", "invalid", "another-store-token")) {
                var request = new MockHttpServletRequest("GET", "/api/v1/stores/STORE_B/" + suffix);
                if (!token.isEmpty()) request.addHeader("Authorization", "Bearer " + token);
                var reached = new java.util.concurrent.atomic.AtomicBoolean();
                filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> {
                    reached.set(true);
                    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                });
                assertThat(reached).isTrue();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            }
        }
        verifyNoInteractions(sessions);
    }

    @Test void anonymousCatalogDoesNotExtendToOtherStoreResourcesOrMethods() {
        for (String path : List.of("/api/v1/stores/A/orders", "/api/v1/stores/A/products/1/private",
                "/api/v1/stores/A/skus", "/api/v1/stores/A/skus/1/inventory",
                "/api/v1/stores/A/products/", "/api/v1/stores/A/products/1/extra",
                "/api/v1/public/stores/A/products", "/api/v1/public/anything",
                "/api/v1/products", "/api/v1/skus/1", "/api/v1/inventory")) {
            assertThat(AuthSecurityFilter.anonymous(new MockHttpServletRequest("GET", path))).as(path).isFalse();
        }
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE", "HEAD")) {
            for (String suffix : List.of("products", "products/1", "skus/1")) {
                assertThat(AuthSecurityFilter.anonymous(new MockHttpServletRequest(method,
                        "/api/v1/stores/A/" + suffix))).as(method + " " + suffix).isFalse();
            }
        }
        for (String action : List.of("sms-codes", "consumer/login")) {
            assertThat(AuthSecurityFilter.anonymous(new MockHttpServletRequest("POST",
                    "/api/v1/stores/A/auth/" + action))).isTrue();
        }
        assertThat(AuthSecurityFilter.anonymous(new MockHttpServletRequest("POST", "/api/v1/auth/staff/login"))).isTrue();
    }
}
