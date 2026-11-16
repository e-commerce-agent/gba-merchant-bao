package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.util.AuthInput;
import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.auth.mapper.AuthTenantMapper;
import com.gba.merchantbao.common.privacy.PhoneMasker;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthInputTest {
    @Test void canonicalIdentifiersAreUnambiguous() {
        assertThat(AuthInput.phone(" 13800138000 ")).isEqualTo(AuthInput.phone(" +8613800138000 "));
        assertThat(PhoneMasker.maskPhone("+8613800138000")).isEqualTo("138****8000");
        assertThat(AuthInput.username(" Alice_1 ")).isEqualTo("alice_1");
        assertThat(AuthInput.merchantCode(" m_3c_demo ")).isEqualTo("M_3C_DEMO");
        assertThat(AuthInput.identifier("13800138000").phone()).isTrue();
        assertThat(AuthInput.identifier("Alice_1").phone()).isFalse();
        for (String s : new String[]{"", "123", "12800138000", "+113800138000", "138 0013 8000"})
            assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.phone(s));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.username("13800138000"));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.storeSlug(null));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.merchantCode("x/y"));
    }
    @Test void passwordsRetainSpacesAndRespectBcryptBytes() {
        assertThat(AuthInput.password(" password ")).isEqualTo(" password ");
        assertThat(AuthInput.password("中".repeat(24))).hasSize(24);
        assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.password("中".repeat(25)));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.password("a".repeat(65)));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthInput.password("short"));
    }
    @Test void publicLocatorResolvesOnlyActiveAndRequiresContext() {
        var mapper = mock(AuthTenantMapper.class);
        when(mapper.findActiveByCode("M_3C_DEMO")).thenReturn(new Tenant(1, "M_3C_DEMO"));
        var resolver = new TenantResolver(mapper);
        assertThat(resolver.resolveActive(AuthInput.storeSlug(" m_3c_demo "))).isPresent();
        assertThat(resolver.resolveActive("DISABLED")).isEmpty();
        assertThat(resolver.resolveActive(null)).isEmpty();
        verify(mapper, never()).findActiveByCode(null);
    }
}
