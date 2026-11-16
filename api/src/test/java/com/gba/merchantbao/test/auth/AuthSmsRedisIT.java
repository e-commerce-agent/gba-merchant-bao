package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.test.support.DockerRequired;
import com.gba.merchantbao.test.support.MutableAuthClock;

import com.gba.merchantbao.auth.cache.AuthRedis;
import com.gba.merchantbao.auth.service.AuthRateLimiter;
import com.gba.merchantbao.auth.service.SmsChallengeService;
import com.gba.merchantbao.auth.service.SmsSender;
import com.gba.merchantbao.auth.util.AuthCrypto;

import com.gba.merchantbao.auth.config.AuthLimits;
import com.gba.merchantbao.auth.config.AuthSmsConfiguration.SmsPolicy;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.legacy.config.RedisConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class AuthSmsRedisIT {
    static { DockerRequired.check(); }
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static LettuceConnectionFactory factory;
    static StringRedisTemplate template;
    AuthRedis redis;
    AuthLimits limits;
    AuthRateLimiter rates;
    AtomicReference<String> delivered;
    SmsChallengeService service;
    MutableAuthClock clock;
    @BeforeAll static void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet(); factory.start(); template = new StringRedisTemplate(factory);
    }
    @AfterAll static void close() { if (factory != null) factory.destroy(); }
    @BeforeEach void setup() {
        redis = new AuthRedis(template, "smsit-" + UUID.randomUUID()); limits = new AuthLimits();
        rates = new AuthRateLimiter(redis, limits); clock = new MutableAuthClock(); delivered = new AtomicReference<>();
        service = service((phone, code) -> delivered.set(code));
    }
    SmsChallengeService service(SmsSender sender) { return new SmsChallengeService(redis, sender, new SmsPolicy(true, false), rates, clock); }
    String key(long merchant, String phone) { return redis.key("sms:" + merchant + ":LOGIN:" + AuthCrypto.hash(phone)); }
    void allowResend(long merchant, String phone) { template.delete(redis.key("sms-cooldown:" + merchant + ":LOGIN:" + AuthCrypto.hash(phone))); }
    @Test void codesAreScopedHashedExpiringAndSingleUse() {
        var result = service.send(1, "+8613800138000", "direct");
        String code = delivered.get(); assertThat(code).matches("[0-9]{6}"); assertThat(result.debugCode()).isNull();
        assertThat(template.getExpire(key(1, "+8613800138000"))).isBetween(299L, 300L);
        assertThat(redis.hash(key(1, "+8613800138000")).values()).doesNotContain(code, "+8613800138000");
        assertThatThrownBy(() -> service.consume(2, "+8613800138000", code)).isInstanceOf(AuthError.class);
        assertThatThrownBy(() -> service.consume(1, "+8613800138001", code)).isInstanceOf(AuthError.class);
        service.consume(1, "+8613800138000", code);
        assertThatThrownBy(() -> service.consume(1, "+8613800138000", code)).isInstanceOf(AuthError.class);
    }
    @Test void expiryResendAndFifthFailureInvalidate() {
        service.send(1, "+8613800138000", "direct"); String code = delivered.get();
        clock.advance(Duration.ofSeconds(301));
        assertThatThrownBy(() -> service.consume(1, "+8613800138000", code)).isInstanceOf(AuthError.class);
        allowResend(1, "+8613800138000"); service.send(1, "+8613800138000", "direct");
        String next = delivered.get(), oldId = (String) redis.hash(key(1, "+8613800138000")).get("challengeId");
        allowResend(1, "+8613800138000"); service.send(1, "+8613800138000", "direct");
        assertThat(redis.run("sms-challenge", List.of(key(1, "+8613800138000")), "CONSUME", oldId, "anything", "0")).isEqualTo("0");
        String wrong = delivered.get().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.consume(1, "+8613800138000", wrong)).isInstanceOf(AuthError.class);
        assertThat(template.hasKey(key(1, "+8613800138000"))).isFalse();
        assertThatThrownBy(() -> service.consume(1, "+8613800138000", next)).isInstanceOf(AuthError.class);
    }
    @Test void concurrentConsumptionHasExactlyOneWinner() throws Exception {
        service.send(1, "+8613800138000", "direct"); String code = delivered.get();
        try (var pool = Executors.newFixedThreadPool(12)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 24; i++) tasks.add(() -> { try { service.consume(1, "+8613800138000", code); return true; } catch (AuthError e) { return false; } });
            long winners = 0; for (var f : pool.invokeAll(tasks)) if (f.get()) winners++;
            assertThat(winners).isEqualTo(1);
        }
    }
    @Test void pendingCannotBeUsedAndOldSendFailureCannotDeleteNewChallenge() {
        SmsChallengeService failing = service((phone, code) -> {
            assertThatThrownBy(() -> service.consume(1, phone, code)).isInstanceOf(AuthError.class);
            allowResend(1, phone); service.send(1, phone, "direct");
            throw new IllegalStateException("send failed");
        });
        assertThatThrownBy(() -> failing.send(1, "+8613800138000", "direct")).isInstanceOf(AuthError.class);
        service.consume(1, "+8613800138000", delivered.get());
        allowResend(1, "+8613800138000");
        assertThatThrownBy(() -> service((p,c) -> { throw new IllegalStateException(); }).send(1, "+8613800138000", "direct")).isInstanceOf(AuthError.class);
        assertThat(template.hasKey(key(1, "+8613800138000"))).isFalse();
    }
    @Test void quotasAreAtomicHaveTtlRecoverAndCoexistWithLegacy() throws Exception {
        limits.setLoginIpPerWindow(3);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (int i=0; i<20; i++) jobs.add(() -> { try { rates.login("direct"); return true; } catch (AuthError e) { assertThat(e.retryAfter()).isPositive(); return false; } });
            long allowed=0; for (var f : pool.invokeAll(jobs)) if (f.get()) allowed++;
            assertThat(allowed).isEqualTo(3);
        }
        String ipKey = redis.key("login-ip:" + AuthCrypto.hash("direct"));
        assertThat(template.getExpire(ipKey)).isBetween(1L, 900L);
        template.delete(ipKey); rates.login("direct");
        service.send(1, "+8613800138000", "direct");
        assertThatThrownBy(() -> service.send(1, "+8613800138000", "direct")).isInstanceOfSatisfying(AuthError.class, e -> assertThat(e.status()).isEqualTo(429));
        String lock=rates.passwordKey(1, 100L, "user");
        assertThat(lock).isEqualTo(rates.passwordKey(1, 100L, "+8613800138000"));
        for (int i=0; i<4; i++) rates.passwordFailed(lock);
        assertThatThrownBy(() -> rates.passwordFailed(lock)).isInstanceOf(AuthError.class);
        assertThat(template.getExpire(lock)).isBetween(1L, 900L);
        template.delete(lock); rates.passwordAllowed(lock);
        var legacy = new RedisConfiguration().redisTemplate(factory); legacy.afterPropertiesSet();
        legacy.opsForValue().set(redis.key("legacy"), "legacy-value");
        assertThat(legacy.opsForValue().get(redis.key("legacy"))).isEqualTo("legacy-value");
        assertThat(redis.hash(key(1, "+8613800138000"))).containsEntry("ready", "1");
    }
    @Test void phoneAndIpSmsQuotasAndRefreshQuotaRecoverWithExpiry() throws Exception {
        limits.setSmsPhonePerHour(2);limits.setSmsIpPerHour(3);
        rates.sms(1,"+8613800138000","one");allowResend(1,"+8613800138000");rates.sms(1,"+8613800138000","two");allowResend(1,"+8613800138000");
        assertThatThrownBy(()->rates.sms(1,"+8613800138000","three")).isInstanceOfSatisfying(AuthError.class,e->assertThat(e.retryAfter()).isPositive());
        for(int i=0;i<3;i++)rates.sms(2,"+861380013800"+i,"same-ip");
        assertThatThrownBy(()->rates.sms(2,"+8613800138009","same-ip")).isInstanceOf(AuthError.class);
        String ip=redis.key("sms-ip:"+AuthCrypto.hash("same-ip"));assertThat(template.getExpire(ip)).isBetween(3590L,3600L);
        // Expire only dedicated test keys; recovery is driven by Redis expiry, not a cleared production namespace.
        template.expire(ip,Duration.ofMillis(1));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(Boolean.TRUE.equals(template.hasKey(ip))&&System.nanoTime()<deadline)Thread.onSpinWait();
        rates.sms(2,"+8613800138009","same-ip");
        limits.setRefreshIpPerWindow(1);rates.refresh("refresh");
        assertThatThrownBy(()->rates.refresh("refresh")).isInstanceOf(AuthError.class);
        String password=rates.passwordKey(1,1L,"user");rates.passwordFailed(password);rates.passwordSucceeded(password);assertThat(template.hasKey(password)).isFalse();
    }
}
