package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.entity.StaffAccountRow;
import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.mapper.ConsumerAccountMapper;
import com.gba.merchantbao.auth.mapper.StaffAccountMapper;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.AccountService;
import com.gba.merchantbao.auth.service.AuthRateLimiter;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.auth.service.SmsChallengeService;

import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.common.id.IdGenerator;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthCredentialTest {
    final TenantResolver tenants=mock(TenantResolver.class);
    final StaffAccountMapper staff=mock(StaffAccountMapper.class);
    final AuthRateLimiter rates=mock(AuthRateLimiter.class);
    final SessionService sessions=mock(SessionService.class);
    final BCryptPasswordEncoder bcrypt=spy(new BCryptPasswordEncoder(12));
    final AccountService accounts=new AccountService(tenants,mock(ConsumerAccountMapper.class),staff,
        mock(SmsChallengeService.class),rates,sessions,mock(IdGenerator.class),Clock.systemUTC(),mock(PlatformTransactionManager.class));
    @BeforeEach void encoder(){ReflectionTestUtils.setField(accounts,"bcrypt",bcrypt);}
    void rejected(String code,String user,String password) {
        assertThatThrownBy(()->accounts.staffLogin(code,user,password,"loopback")).isInstanceOf(AuthError.class)
            .satisfies(e->{var error=(AuthError)e;assertThat(error.status()).isEqualTo(401);assertThat(error.code().getCode()).isEqualTo(11001);});
    }
    @Test void missingMerchantAndAccountStillPerformRealDummyBCrypt() {
        when(tenants.resolveActive("M_UNKNOWN")).thenReturn(Optional.empty());
        when(tenants.resolveActive("M_DEMO")).thenReturn(Optional.of(new Tenant(1,"M_DEMO")));
        rejected("M_UNKNOWN","unknown_staff","WrongOnly-2026!");
        rejected("M_DEMO","unknown_staff","WrongOnly-2026!");
        verify(bcrypt,times(2)).matches(eq("WrongOnly-2026!"),argThat(h->h.matches("\\$2[aby]\\$12\\$.{53}")));
        verifyNoInteractions(sessions);
    }
    @Test void sameUsernameUsesTenantPasswordAndPrincipal() {
        when(tenants.resolveActive("M_A")).thenReturn(Optional.of(new Tenant(1,"M_A")));
        when(tenants.resolveActive("M_B")).thenReturn(Optional.of(new Tenant(2,"M_B")));
        when(staff.findByUsername(1,"same_staff")).thenReturn(row(11,1,"ACTIVE","TenantA-2026!"));
        when(staff.findByUsername(2,"same_staff")).thenReturn(row(22,2,"ACTIVE","TenantB-2026!"));
        accounts.staffLogin("M_A","same_staff","TenantA-2026!","loopback");
        rejected("M_B","same_staff","TenantA-2026!");
        accounts.staffLogin("M_B","same_staff","TenantB-2026!","loopback");
        verify(sessions).login(new AuthPrincipal(11,1,AccountRole.MERCHANT_STAFF));
        verify(sessions).login(new AuthPrincipal(22,2,AccountRole.MERCHANT_STAFF));
    }
    @Test void disabledAndWrongPasswordHaveIdenticalCredentialErrors() {
        when(tenants.resolveActive("M_A")).thenReturn(Optional.of(new Tenant(1,"M_A")));
        when(staff.findByUsername(1,"disabled")).thenReturn(row(11,1,"DISABLED","Correct-2026!"));
        when(staff.findByUsername(1,"enabled")).thenReturn(row(12,1,"ACTIVE","Correct-2026!"));
        rejected("M_A","disabled","Correct-2026!");rejected("M_A","enabled","WrongOnly-2026!");
        verifyNoInteractions(sessions);
    }
    StaffAccountRow row(long id,long merchant,String status,String password) {
        return new StaffAccountRow(id,merchant,"same_staff","+8613800000000","Staff",new BCryptPasswordEncoder(12).encode(password),status,Instant.now(),Instant.now(),null,0);
    }
}
