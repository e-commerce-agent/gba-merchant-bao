package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.auth.service.AccessTokenService;
import com.gba.merchantbao.auth.config.AuthJwtKeyMaterial;
import com.gba.merchantbao.auth.config.AuthJwtProperties;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.exception.InvalidAccessTokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.gba.merchantbao.test.auth.AuthJwtConfigurationTest.*;
import static org.assertj.core.api.Assertions.*;

class AccessTokenServiceTest {
    static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    static final AuthJwtProperties PROPERTIES = new AuthJwtProperties(STAFF, CONSUMER, "merchantbao", "api-v1");
    static final AuthJwtKeyMaterial KEYS = AuthJwtKeyMaterial.validate(PROPERTIES);
    static final AccessTokenService SERVICE = new AccessTokenService(KEYS, PROPERTIES);

    @Test
    void bothRolesRoundTripWithIndependentSigningKeysAndCompleteClaims() {
        for (AccountRole role : AccountRole.values()) {
            var principal = new AuthPrincipal(101, 202, role);
            var issued = SERVICE.issue(principal, "session-1", NOW, NOW.plusSeconds(86400));
            var verified = SERVICE.verify(issued.token(), NOW);
            assertThat(verified.principal()).isEqualTo(principal);
            assertThat(verified.sessionId()).isEqualTo("session-1");
            assertThat(verified.tokenId()).isEqualTo(issued.tokenId()).isNotEqualTo(verified.sessionId());
            assertThat(verified.issuedAt()).isEqualTo(NOW);
            assertThat(verified.expiresAt()).isEqualTo(NOW.plusSeconds(7200));
            SecretKey key = role == AccountRole.MERCHANT_STAFF ? KEYS.merchantStaffKey() : KEYS.consumerKey();
            var jwt = Jwts.parser().verifyWith(key).clock(() -> Date.from(NOW)).build().parseSignedClaims(issued.token());
            assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("HS256");
            assertThat(jwt.getPayload().getIssuer()).isEqualTo("merchantbao");
            assertThat(jwt.getPayload().getAudience()).isEqualTo(Set.of("api-v1"));
            assertThat(jwt.getPayload().keySet()).containsExactlyInAnyOrder("iss", "aud", "sub", "merchantId", "role", "sid", "jti", "iat", "exp");
            assertThat(issued.toString()).doesNotContain(issued.token());
            assertThat(SERVICE.issue(principal, "session-1", NOW, NOW.plusSeconds(86400)).tokenId()).isNotEqualTo(issued.tokenId());
        }
    }

    @Test
    void expiryIsCappedByOriginalAbsoluteDeadlineIncludingSubseconds() {
        var principal = new AuthPrincipal(1, 2, AccountRole.CONSUMER);
        var issued = SERVICE.issue(principal, "sid", NOW.plusMillis(123), NOW.plusSeconds(59).plusMillis(999));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(59));
        assertThatThrownBy(() -> SERVICE.verify(issued.token(), NOW.plusSeconds(59))).isInstanceOf(InvalidAccessTokenException.class);
        for (Instant deadline : new Instant[]{NOW.minusSeconds(1), NOW, NOW.plusMillis(900)}) {
            assertThatThrownBy(() -> SERVICE.issue(principal, "sid", NOW, deadline)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void tamperingUnknownKeysAndAlgorithmSubstitutionNeverProduceIdentity() {
        String valid = signed(claims());
        String[] parts = valid.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"CONSUMER\",\"sub\":\"999\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rejects(parts[0] + "." + forgedPayload + "." + parts[2]);
        rejects(Jwts.builder().claims(claims()).signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(key('X', 32))), Jwts.SIG.HS256).compact());
        rejects(Jwts.builder().claims(claims()).compact());
        rejects(Jwts.builder().claims(claims()).signWith(Keys.hmacShaKeyFor(new byte[48]), Jwts.SIG.HS384).compact());
        rejects(Jwts.builder().claims(claims()).signWith(Keys.hmacShaKeyFor(new byte[64]), Jwts.SIG.HS512).compact());
        for (String token : new String[]{"", "not-a-token", "a.b.c"}) rejects(token);
        rejects(null);
        var longProperties = new AuthJwtProperties(key('L', 64), key('M', 64), "merchantbao", "api-v1");
        var longKeys = AuthJwtKeyMaterial.validate(longProperties);
        var longService = new AccessTokenService(longKeys, longProperties);
        var issued = longService.issue(new AuthPrincipal(1, 2, AccountRole.MERCHANT_STAFF), "sid", NOW, NOW.plusSeconds(10000));
        assertThat(longService.verify(issued.token(), NOW).principal().accountId()).isEqualTo(1);
        for (var algorithm : java.util.List.of(Jwts.SIG.HS384, Jwts.SIG.HS512)) {
            String substituted = Jwts.builder().claims(claims()).signWith(longKeys.merchantStaffKey(), algorithm).compact();
            assertThatThrownBy(() -> longService.verify(substituted, NOW)).isInstanceOf(InvalidAccessTokenException.class);
        }
    }

    @Test
    void missingClaimsAndMalformedIdentityAreRejectedAfterSignatureVerification() {
        for (String field : claims().keySet()) {
            Map<String, Object> values = claims();
            values.remove(field);
            rejects(signed(values));
        }
        for (String field : new String[]{"sub", "merchantId"}) {
            for (Object bad : new Object[]{"0", "-1", "01", "9223372036854775808", "abc", " ", 123}) {
                var values = claims();
                values.put(field, bad);
                // JJWT normalizes numeric sub to String; merchantId must already be a string.
                if (field.equals("sub") && bad instanceof Integer) continue;
                rejects(signed(values));
            }
        }
        for (String field : new String[]{"sid", "jti"}) {
            var values = claims();
            values.put(field, " ");
            rejects(signed(values));
        }
    }

    @Test
    void wrongIssuerAudienceRoleKeyAndLifetimeAreRejected() {
        for (var entry : Map.<String, Object>ofEntries(
                Map.entry("iss", "other"), Map.entry("aud", Set.of("other")),
                Map.entry("role", "ADMIN"), Map.entry("sid", "token-1"),
                Map.entry("iat", Date.from(NOW.plusSeconds(1))), Map.entry("exp", Date.from(NOW)),
                Map.entry("empId", 1L), Map.entry("userId", 1L)).entrySet()) {
            var values = claims();
            values.put(entry.getKey(), entry.getValue());
            rejects(signed(values));
        }
        var values = claims();
        values.put("aud", Set.of("api-v1", "other"));
        rejects(signed(values));
        values = claims();
        values.put("exp", Date.from(NOW.plusSeconds(7201)));
        rejects(signed(values));
        values = claims();
        values.put("role", "CONSUMER"); // Correctly signed with the wrong role's key.
        rejects(signed(values));
        rejects(Jwts.builder().claims(claims()).signWith(KEYS.consumerKey(), Jwts.SIG.HS256).compact());
        values = claims();
        values.put("iat", Date.from(NOW.minusSeconds(10)));
        values.put("exp", Date.from(NOW.minusSeconds(11)));
        rejects(signed(values));
    }

    private static Map<String, Object> claims() {
        var values = new LinkedHashMap<String, Object>();
        values.put("iss", "merchantbao"); values.put("aud", Set.of("api-v1"));
        values.put("sub", "101"); values.put("merchantId", "202");
        values.put("role", "MERCHANT_STAFF"); values.put("sid", "session-1"); values.put("jti", "token-1");
        values.put("iat", Date.from(NOW)); values.put("exp", Date.from(NOW.plusSeconds(7200)));
        return values;
    }

    private static String signed(Map<String, Object> claims) {
        return Jwts.builder().claims(claims).signWith(KEYS.merchantStaffKey(), Jwts.SIG.HS256).compact();
    }

    private static void rejects(String token) {
        assertThatThrownBy(() -> SERVICE.verify(token, NOW)).isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("Invalid access token").hasNoCause();
    }
}
