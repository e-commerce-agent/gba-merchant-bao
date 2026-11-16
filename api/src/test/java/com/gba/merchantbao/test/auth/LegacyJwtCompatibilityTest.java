package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.exception.InvalidAccessTokenException;
import com.gba.merchantbao.legacy.utils.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static com.gba.merchantbao.test.auth.AuthJwtConfigurationTest.*;
import static com.gba.merchantbao.test.auth.AccessTokenServiceTest.*;
import static org.assertj.core.api.Assertions.*;

class LegacyJwtCompatibilityTest {
    @Test
    void legacyTokensUseBase64BytesAndExplicitHs256EvenWithLongKeys() {
        for (String encoded : new String[]{STAFF, key('L', 64)}) {
            for (String identity : new String[]{"empId", "userId"}) {
                String token = JwtUtil.createJWT(encoded, 7200000, Map.of(identity, 101L));
                assertThat(JwtUtil.parseJWT(encoded, token).get(identity, Long.class)).isEqualTo(101L);
                var parsed = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(encoded)))
                        .build().parseSignedClaims(token);
                assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
            }
        }
    }

    @Test
    void weakOrNonBase64LegacyPasswordsAreNotPadded() {
        for (String invalid : new String[]{"itcast", "itheima", "not-base64!", key('S', 31)}) {
            assertThatThrownBy(() -> JwtUtil.createJWT(invalid, 1000, Map.of("empId", 1L)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void newAndLegacyTokensCannotCrossProtocolsEvenWhenUsingTheSameKey() {
        for (AccountRole role : AccountRole.values()) {
            String encoded = role == AccountRole.MERCHANT_STAFF ? STAFF : CONSUMER;
            String identity = role == AccountRole.MERCHANT_STAFF ? "empId" : "userId";
            String oldToken = JwtUtil.createJWT(encoded, 7200000, Map.of(identity, 101L));
            assertThatThrownBy(() -> SERVICE.verify(oldToken, NOW)).isInstanceOf(InvalidAccessTokenException.class);
            String newToken = SERVICE.issue(new AuthPrincipal(101, 202, role), "sid", NOW, NOW.plusSeconds(86400)).token();
            assertThatThrownBy(() -> JwtUtil.parseJWT(encoded, newToken)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> JwtUtil.parseJWT(role == AccountRole.MERCHANT_STAFF ? CONSUMER : STAFF, oldToken))
                    .isInstanceOf(RuntimeException.class);
        }
        String mixed = JwtUtil.createJWT(STAFF, 7200000, Map.of("empId", 1L, "userId", 2L));
        assertThatThrownBy(() -> JwtUtil.parseJWT(STAFF, mixed)).isInstanceOf(RuntimeException.class);
    }
}
