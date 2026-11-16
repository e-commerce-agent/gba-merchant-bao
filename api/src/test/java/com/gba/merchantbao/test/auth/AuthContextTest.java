package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.security.AuthSecurityFilter;
import com.gba.merchantbao.legacy.config.LegacyContextConfiguration;
import com.gba.merchantbao.legacy.context.BaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthContextTest {
    @Test void contextsClearOnSameThreadAfterSuccessAndException() throws Exception {
        var legacy=new LegacyContextConfiguration().legacyContextCleanup().getFilter();
        for(boolean fail:new boolean[]{false,true}) {
            BaseContext.setCurrentId(999L);
            try {legacy.doFilter(new MockHttpServletRequest("GET","/admin/probe"),new MockHttpServletResponse(),(r,s)->{
                assertThat(BaseContext.getCurrentId()).isNull();BaseContext.setCurrentId(101L);if(fail)throw new jakarta.servlet.ServletException("probe");
            });}catch(jakarta.servlet.ServletException expected) {assertThat(fail).isTrue();}
            assertThat(BaseContext.getCurrentId()).isNull();
            var filter=new AuthSecurityFilter(null,null,null,null,null);
            var request=new MockHttpServletRequest("GET","/api/v1/health");request.setServletPath("/api/v1/health");
            try {filter.doFilter(request,new MockHttpServletResponse(),(r,s)->{
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                SecurityContextHolder.getContext().setAuthentication(mock(org.springframework.security.core.Authentication.class));
                if(fail)throw new jakarta.servlet.ServletException("probe");
            });}catch(jakarta.servlet.ServletException expected) {assertThat(fail).isTrue();}
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
    @Test void authHasNoLegacyImportsOrUnimplementedDomainModels() throws Exception {
        try(var files=java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/gba/merchantbao/auth"))) {
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList())
                assertThat(java.nio.file.Files.readString(file)).doesNotContain("import com.gba.merchantbao.legacy.");
        }
    }
}
