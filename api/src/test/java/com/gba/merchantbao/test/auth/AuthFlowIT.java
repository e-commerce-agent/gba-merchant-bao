package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.test.support.DockerRequired;
import com.gba.merchantbao.test.support.MutableAuthClock;

import com.gba.merchantbao.auth.cache.AuthRedis;
import com.gba.merchantbao.auth.entity.ConsumerAccountRow;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.mapper.ConsumerAccountMapper;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.AccessTokenService;
import com.gba.merchantbao.auth.service.AccountDirectory;
import com.gba.merchantbao.auth.service.AccountService;
import com.gba.merchantbao.auth.service.AuthRateLimiter;
import com.gba.merchantbao.auth.service.SessionService;
import com.gba.merchantbao.auth.service.SmsChallengeService;
import com.gba.merchantbao.auth.service.SmsSender;
import com.gba.merchantbao.auth.util.AuthCrypto;

import com.fasterxml.jackson.databind.*;
import com.gba.merchantbao.auth.config.AuthLimits;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext
@Import(AuthFlowIT.TimeConfig.class)
@Sql(scripts="/db/fixture/auth_accounts.sql",executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuthFlowIT {
    static {DockerRequired.check();}
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.34").withDatabaseName("auth_flow").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r){
        // BEFORE_TEST_CLASS @Sql needs the context before the extension's beforeAll callback.
        if(!MYSQL.isRunning())MYSQL.start();if(!REDIS.isRunning())REDIS.start();
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableAuthClock controlledClock(){return new MutableAuthClock();}}
    @Autowired MutableAuthClock time; @Autowired SessionService sessions;@Autowired AccessTokenService access;
    @Autowired AccountService accounts;@Autowired SmsChallengeService sms;@Autowired AccountDirectory directory;
    @Autowired AuthRateLimiter rates;@Autowired AuthLimits limits;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuthRedis authRedis;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ConsumerAccountMapper consumerMapper;
    @Autowired StringRedisTemplate redis;@Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;@Autowired ObjectMapper json;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockitoBean SmsSender sender;
    final Map<String,String> codes=new ConcurrentHashMap<>();
    @BeforeEach void prepare(){
        // This dedicated ephemeral Redis contains no application data outside this test namespace.
        var keys=redis.keys(authRedis.key("*"));if(keys!=null&&!keys.isEmpty())redis.delete(keys);
        time.advance(Duration.between(time.instant(),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        limits.setLoginIpPerWindow(30);limits.setRefreshIpPerWindow(30);limits.setSmsCooldownSeconds(60);
        doAnswer(i->{codes.put(i.getArgument(0),i.getArgument(1));return null;}).when(sender).sendLoginCode(anyString(),anyString());
    }
    long tenant(){return jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code='M_3C_DEMO'",Long.class);}
    AuthPrincipal consumer(){return new AuthPrincipal(2000000000000201L,tenant(),AccountRole.CONSUMER);}
    AuthPrincipal staff(){return new AuthPrincipal(2000000000000101L,tenant(),AccountRole.MERCHANT_STAFF);}
    String sid(SessionService.Tokens t){return access.verify(t.accessToken(),time.instant()).sessionId();}
    String key(String suffix){return authRedis.key(suffix);}
    void denied(Runnable action){assertThatThrownBy(action::run).isInstanceOf(AuthError.class).satisfies(e->assertThat(((AuthError)e).status()).isEqualTo(401));}
    SessionService.Tokens rotate(SessionService.Tokens t){return sessions.refresh(t.refreshToken(),UUID.randomUUID().toString());}
    ResponseEntity<String> request(HttpMethod method,String path,Object body,String token){
        var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("X-Request-Id","auth-it-correlation");if(token!=null)h.setBearerAuth(token);
        var response=http.exchange(path,method,new HttpEntity<>(body,h),String.class);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("auth-it-correlation");
        assertThat(response.getBody()).contains("\"requestId\":\"auth-it-correlation\"");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");assertThat(response.getHeaders().get("Set-Cookie")).isNull();return response;
    }
    JsonNode data(ResponseEntity<String> response)throws Exception{return json.readTree(response.getBody()).path("data");}
    @Test void initialSessionsAreIndependentAndContainOnlyDigests() {
        var a=sessions.login(consumer());var b=sessions.login(consumer());var s=sessions.login(staff());
        assertThat(sid(a)).isNotEqualTo(sid(b));assertThat(a.expiresIn()).isEqualTo(7200);
        assertThat(Duration.between(time.instant(),a.refreshExpiresAt())).isEqualTo(Duration.ofDays(30));
        assertThat(Duration.between(time.instant(),s.refreshExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(redis.getExpire(key("session:"+sid(a)))).isBetween(604790L,604800L);
        assertThat(redis.getExpire(key("session:"+sid(s)))).isBetween(86390L,86400L);
        assertThat(redis.opsForValue().get(key("session:"+sid(a)))).doesNotContain(a.accessToken(),a.refreshToken());
        assertThat(redis.opsForHash().entries(key("control:"+sid(a))).toString()).doesNotContain(a.accessToken(),a.refreshToken());
        assertThat(redis.hasKey(key("refresh:"+AuthCrypto.hash(a.refreshToken())))).isTrue();
        assertThat(sessions.authenticate(a.accessToken()).principal()).isEqualTo(consumer());
    }
    @Test void rotationRetryIsEncryptedIdempotentAndReplayRevokesOnlyItsDevice() {
        var a=sessions.login(consumer());var other=sessions.login(consumer());String id=UUID.randomUUID().toString();
        var first=sessions.refresh(a.refreshToken(),id);String state=redis.opsForValue().get(key("session:"+sid(a)));
        time.advance(Duration.ofSeconds(3));var retry=sessions.refresh(a.refreshToken(),id);
        assertThat(retry).isEqualTo(first);assertThat(redis.opsForValue().get(key("session:"+sid(a)))).isEqualTo(state);
        String cached=redis.opsForValue().get(key("refresh-retry:"+sid(a)+":"+id));assertThat(cached).doesNotContain(first.accessToken(),first.refreshToken());
        denied(()->sessions.refresh(AuthCrypto.randomToken(),UUID.randomUUID().toString()));sessions.authenticate(first.accessToken());
        denied(()->sessions.refresh(a.refreshToken(),UUID.randomUUID().toString()));denied(()->sessions.authenticate(first.accessToken()));sessions.authenticate(other.accessToken());
        denied(()->sessions.refresh(a.refreshToken(),id));
    }
    @Test void oldGenerationWindowAndTamperedCacheNeverReturnCredentials() {
        var a=sessions.login(consumer());String id=UUID.randomUUID().toString();var b=sessions.refresh(a.refreshToken(),id);var c=rotate(b);
        denied(()->sessions.refresh(a.refreshToken(),id));denied(()->sessions.authenticate(c.accessToken()));
        var nextSession=sessions.login(consumer());String token=nextSession.refreshToken();String requestId=UUID.randomUUID().toString();
        sessions.refresh(token,requestId);time.advance(Duration.ofSeconds(11));
        // Redis wall time need not move: application absolute/window time is independently checked.
        denied(()->sessions.refresh(token,requestId));
        var d=sessions.login(consumer());String did=UUID.randomUUID().toString();sessions.refresh(d.refreshToken(),did);
        redis.opsForValue().set(key("refresh-retry:"+sid(d)+":"+did),"tampered",Duration.ofSeconds(10));denied(()->sessions.refresh(d.refreshToken(),did));
    }
    @Test void idleCanRecoverButAbsoluteAndLostControlOrIndexCannot() {
        for(var p:List.of(consumer(),staff())) {
            var a=sessions.login(p);String sid=sid(a);redis.delete(key("session:"+sid));denied(()->sessions.authenticate(a.accessToken()));
            time.advance(Duration.ofSeconds(SessionService.idle(p.role())+1));var b=rotate(a);
            assertThat(b.refreshExpiresAt()).isEqualTo(a.refreshExpiresAt());sessions.authenticate(b.accessToken());
            time.advance(Duration.between(time.instant(),a.refreshExpiresAt()).minusSeconds(5));var c=rotate(b);assertThat(c.expiresIn()).isEqualTo(5);
            time.advance(Duration.ofSeconds(5));denied(()->rotate(c));
            time.advance(Duration.between(time.instant(),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        }
        var d=sessions.login(consumer());redis.delete(key("control:"+sid(d)));denied(()->rotate(d));denied(()->sessions.authenticate(d.accessToken()));
        var missing=sessions.login(consumer());String token=missing.refreshToken();redis.delete(key("refresh:"+AuthCrypto.hash(token)));denied(()->sessions.refresh(token,UUID.randomUUID().toString()));
    }
    @Test void logoutIsIdempotentSupportsHistoricalRefreshAndKeepsOtherDevices() {
        var a=sessions.login(consumer());var other=sessions.login(consumer());var b=rotate(a);
        assertThatThrownBy(()->sessions.logout(other.accessToken(),a.refreshToken())).isInstanceOf(AuthError.class).satisfies(e->assertThat(((AuthError)e).status()).isEqualTo(400));
        sessions.logout("invalid",a.refreshToken());sessions.logout(null,b.refreshToken());sessions.logout(null,a.refreshToken());
        denied(()->sessions.authenticate(b.accessToken()));denied(()->rotate(b));sessions.authenticate(other.accessToken());
        assertThat(redis.opsForHash().get(key("control:"+sid(a)),"state")).isEqualTo("REVOKED");
        denied(()->sessions.logout(null,AuthCrypto.randomToken()));
        var expired=sessions.login(consumer());time.advance(Duration.ofHours(3));sessions.logout(expired.accessToken(),expired.refreshToken());
    }
    @Test void touchOnlyOnSuccessfulAllowedWorkAndCannotResurrectLogout()throws Exception {
        var a=sessions.login(consumer());Actor actor=sessions.authenticate(a.accessToken());String k=key("session:"+sid(a));String original=redis.opsForValue().get(k);
        time.advance(Duration.ofSeconds(10));request(HttpMethod.GET,"/api/v1/auth/me",null,a.accessToken());assertThat(redis.opsForValue().get(k)).isEqualTo(original);
        request(HttpMethod.GET,"/api/v1/consumer-accounts/9999",null,a.accessToken());assertThat(redis.opsForValue().get(k)).isEqualTo(original);
        request(HttpMethod.GET,"/api/v1/consumer-accounts/"+consumer().accountId(),null,a.accessToken());assertThat(redis.opsForValue().get(k)).isNotEqualTo(original);
        sessions.logout(a.accessToken(),null);sessions.touch(actor);assertThat(redis.hasKey(k)).isFalse();
    }
    @Test void concurrentRefreshAndLogoutNeverResurrects()throws Exception {
        try(var pool=Executors.newFixedThreadPool(8)) {
            for(int n=0;n<8;n++) {
                var a=sessions.login(consumer());String s=sid(a);var start=new CountDownLatch(1);
                var rotation=pool.submit(()->{start.await();try{rotate(a);}catch(AuthError expected){}return null;});
                var logout=pool.submit(()->{start.await();sessions.logout(null,a.refreshToken());return null;});
                start.countDown();rotation.get();logout.get();assertThat(redis.hasKey(key("session:"+s))).isFalse();
                assertThat(redis.opsForHash().get(key("control:"+s),"state")).isEqualTo("REVOKED");
            }
        }
    }
    @Test void staffLoginSharesIdentifierLockAndAllCredentialFailuresLookTheSame() {
        var one=accounts.staffLogin("m_3c_demo"," FIXTURE_STAFF ","FixtureOnly-Auth-2026!","staff1");
        var two=accounts.staffLogin("M_3C_DEMO","13900000301","FixtureOnly-Auth-2026!","staff2");
        assertThat(one.principal().accountId()).isEqualTo(two.principal().accountId());
        for(int n=0;n<4;n++){String identifier=n%2==0?"fixture_staff":"13900000301";denied(()->accounts.staffLogin("M_3C_DEMO",identifier,"wrong-password","staff3"));}
        assertThatThrownBy(()->accounts.staffLogin("M_3C_DEMO","13900000301","wrong-password","staff3")).isInstanceOf(AuthError.class).satisfies(e->assertThat(((AuthError)e).status()).isEqualTo(429));
        for(String merchant:List.of("M_UNKNOWN","M_ISOLATION_TEST"))denied(()->accounts.staffLogin(merchant,"unknown_staff","wrong-password","unknown"));
    }
    @Test void consumerFirstLoginCommitsOnceAndDisabledCannotRecreate() {
        String phone="+8613900000501";sms.send(tenant(),phone,"new-consumer");String code=codes.get(phone);
        var a=accounts.consumerLogin("M_3C_DEMO",phone,code,"new-consumer");
        assertThatThrownBy(()->accounts.consumerLogin("M_3C_DEMO",phone,code,"new-consumer")).isInstanceOf(AuthError.class);
        redis.delete(key("sms-cooldown:"+tenant()+":LOGIN:"+AuthCrypto.hash(phone)));sms.send(tenant(),phone,"new-consumer");
        var b=accounts.consumerLogin("M_3C_DEMO",phone,codes.get(phone),"new-consumer");assertThat(a.principal().accountId()).isEqualTo(b.principal().accountId());
        jdbc.update("UPDATE consumer_account SET status='DISABLED' WHERE id=?",Long.parseLong(a.principal().accountId().asString()));
        redis.delete(key("sms-cooldown:"+tenant()+":LOGIN:"+AuthCrypto.hash(phone)));sms.send(tenant(),phone,"new-consumer");
        denied(()->accounts.consumerLogin("M_3C_DEMO",phone,codes.get(phone),"new-consumer"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE merchant_id=? AND phone=?",Integer.class,tenant(),phone)).isEqualTo(1);
    }
    @Test void httpContractStrictInputRolesResourcesAndDedicatedBranches()throws Exception {
        assertThat(request(HttpMethod.POST,"/api/v1/staff-accounts","{broken",null).getStatusCode().value()).isEqualTo(401);
        var c=sessions.login(consumer());var s=sessions.login(staff());
        assertThat(request(HttpMethod.POST,"/api/v1/staff-accounts","{broken",c.accessToken()).getStatusCode().value()).isEqualTo(403);
        var body=new HashMap<String,Object>(Map.of("username","controlled_staff","phone","13900000502","displayName","Controlled","password","Controlled-2026!"));body.put("merchantId","1");
        assertThat(request(HttpMethod.POST,"/api/v1/staff-accounts",body,s.accessToken()).getStatusCode().value()).isEqualTo(400);body.remove("merchantId");
        var created=request(HttpMethod.POST,"/api/v1/staff-accounts",body,s.accessToken());assertThat(created.getStatusCode().value()).isEqualTo(201);
        var view=data(created);assertThat(view.path("accountId").isTextual()).isTrue();assertThat(view.path("username").asText()).isEqualTo("controlled_staff");
        assertThat(created.getHeaders().getLocation().toString()).isEqualTo("/api/v1/staff-accounts/"+view.path("accountId").asText());assertThat(created.getBody()).doesNotContain("Controlled-2026!","password","Hash");
        var saved=jdbc.queryForMap("SELECT merchant_id,created_by,password_hash FROM merchant_staff_account WHERE id=?",view.path("accountId").asText());
        assertThat(((Number)saved.get("merchant_id")).longValue()).isEqualTo(tenant());
        assertThat(((Number)saved.get("created_by")).longValue()).isEqualTo(staff().accountId());
        assertThat((String)saved.get("password_hash")).startsWith("$2a$12$");
        assertThat(org.springframework.security.crypto.bcrypt.BCrypt.checkpw("Controlled-2026!",(String)saved.get("password_hash"))).isTrue();
        assertThat(request(HttpMethod.POST,"/api/v1/staff-accounts",body,s.accessToken()).getStatusCode().value()).isEqualTo(409);
        assertThat(request(HttpMethod.GET,"/api/v1/staff-accounts/2000000000000102",null,s.accessToken()).getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.GET,"/api/v1/consumer-accounts/2000000000000202",null,c.accessToken()).getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.GET,"/api/v1/consumer-accounts/2000000000000203",null,c.accessToken()).getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.POST,"/api/v1/echo",Map.of("message",""),null).getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.POST,"/api/v1/echo",Map.of("message",""),c.accessToken()).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.GET,"/api/v1/auth/me",null,null).getStatusCode().value()).isEqualTo(401);
        var refreshed=request(HttpMethod.POST,"/api/v1/auth/refresh",Map.of("refreshToken",c.refreshToken(),"refreshRequestId",UUID.randomUUID().toString()),"invalid");assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        assertThat(data(refreshed).path("refreshExpiresAt").asText()).isEqualTo(c.refreshExpiresAt().toString());
        assertThat(request(HttpMethod.POST,"/api/v1/auth/logout",Map.of("refreshToken",c.refreshToken()),"invalid").getStatusCode().value()).isEqualTo(200);
        assertThat(request(HttpMethod.POST,"/api/v1/auth/refresh",Map.of("refreshToken",c.refreshToken(),"refreshRequestId",UUID.randomUUID().toString()),null).getStatusCode().value()).isEqualTo(401);
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void subjectDisableRejectsPrivateAndRefreshButAllowsLogout() {
        var a=sessions.login(staff());jdbc.update("UPDATE merchant_staff_account SET status='DISABLED' WHERE id=?",staff().accountId());
        try {denied(()->sessions.authenticate(a.accessToken()));denied(()->rotate(a));sessions.logout(a.accessToken(),null);}
        finally{jdbc.update("UPDATE merchant_staff_account SET status='ACTIVE' WHERE id=?",staff().accountId());}
        var other=sessions.login(consumer());String token=other.accessToken();String refresh=other.refreshToken();jdbc.update("UPDATE merchant SET status='DISABLED' WHERE id=?",tenant());
        try{denied(()->sessions.authenticate(token));denied(()->sessions.refresh(refresh,UUID.randomUUID().toString()));sessions.logout(token,null);}
        finally{jdbc.update("UPDATE merchant SET status='ACTIVE' WHERE merchant_code='M_3C_DEMO'");}
    }
    @Test void forwardedHeaderCannotBypassRemoteAddressQuota()throws Exception {
        limits.setLoginIpPerWindow(1);
        var body=Map.of("merchantCode","M_UNKNOWN","identifier","unknown_staff","password","wrong-password");
        assertThat(request(HttpMethod.POST,"/api/v1/auth/staff/login",body,null).getStatusCode().value()).isEqualTo(401);
        var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("X-Forwarded-For","203.0.113.99");
        var second=http.exchange("/api/v1/auth/staff/login",HttpMethod.POST,new HttpEntity<>(body,h),String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(429);assertThat(second.getHeaders().getFirst("Retry-After")).isNotBlank();assertThat(second.getBody()).contains("11003");
    }
    @Test void independentChallengesRacingFirstLoginReuseOneCommittedAccount() throws Exception {
        String phone="+8613900000551";long merchant=tenant();var selected=new CountDownLatch(2);var firstConsumed=new CountDownLatch(1);var reads=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation->{
            ConsumerAccountRow found=sqlSession.selectOne("com.gba.merchantbao.auth.mapper.ConsumerAccountMapper.findByPhone",Map.of("merchantId",merchant,"phone",phone));
            if(reads.incrementAndGet()<=2){firstConsumed.countDown();selected.countDown();assertThat(selected.await(5,TimeUnit.SECONDS)).isTrue();}
            return found;
        }).when(consumerMapper).findByPhone(merchant,phone);
        try(var pool=Executors.newFixedThreadPool(2)) {
            sms.send(merchant,phone,"race-one");String code=codes.get(phone);
            var first=pool.submit(()->accounts.consumerLogin("M_3C_DEMO",phone,code,"race-one"));
            assertThat(firstConsumed.await(5,TimeUnit.SECONDS)).isTrue();
            redis.delete(key("sms-cooldown:"+merchant+":LOGIN:"+AuthCrypto.hash(phone)));sms.send(merchant,phone,"race-two");String secondCode=codes.get(phone);
            var second=pool.submit(()->accounts.consumerLogin("M_3C_DEMO",phone,secondCode,"race-two"));
            assertThat(first.get().principal().accountId()).isEqualTo(second.get().principal().accountId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE merchant_id=? AND phone=?",Integer.class,merchant,phone)).isEqualTo(1);
        } finally {reset(consumerMapper);}
    }
    @Test void databaseFailureConsumesChallengeAndSessionFailureKeepsCommittedAccount() {
        String phone="+8613900000552";long merchant=tenant();sms.send(merchant,phone,"fault");String code=codes.get(phone);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test unavailable")).when(consumerMapper).findByPhone(merchant,phone);
        try {assertThatThrownBy(()->accounts.consumerLogin("M_3C_DEMO",phone,code,"fault")).isInstanceOfSatisfying(AuthError.class,e->assertThat(e.status()).isEqualTo(503));}
        finally {reset(consumerMapper);}
        assertThatThrownBy(()->accounts.consumerLogin("M_3C_DEMO",phone,code,"fault")).isInstanceOfSatisfying(AuthError.class,e->assertThat(e.status()).isEqualTo(400));
        redis.delete(key("sms-cooldown:"+merchant+":LOGIN:"+AuthCrypto.hash(phone)));sms.send(merchant,phone,"fault");String next=codes.get(phone);
        doThrow(AuthError.dependency()).when(authRedis).run(eq("session-create"),anyList(),any(String[].class));
        try {
            assertThatThrownBy(()->accounts.consumerLogin("M_3C_DEMO",phone,next,"fault")).isInstanceOfSatisfying(AuthError.class,e->assertThat(e.status()).isEqualTo(503));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE merchant_id=? AND phone=?",Integer.class,merchant,phone)).isEqualTo(1);
            assertThat(redis.keys(key("control:*"))).isEmpty();
        } finally {reset(authRedis);}
    }
    @Test void concurrentSameRefreshRequestReturnsOnePairAndDependencyFaultsUse503() throws Exception {
        var a=sessions.login(consumer());String id=UUID.randomUUID().toString();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var jobs=new ArrayList<Callable<SessionService.Tokens>>();for(int i=0;i<12;i++)jobs.add(()->sessions.refresh(a.refreshToken(),id));
            var results=pool.invokeAll(jobs);var first=results.getFirst().get();for(var result:results)assertThat(result.get()).isEqualTo(first);
        }
        doThrow(AuthError.dependency()).when(authRedis).run(eq("session-revoke"),anyList(),any(String[].class));
        try {assertThat(request(HttpMethod.POST,"/api/v1/auth/logout",Map.of("refreshToken",a.refreshToken()),null).getStatusCode().value()).isEqualTo(503);}
        finally {reset(authRedis);}
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test unavailable")).when(consumerMapper).findById(consumer().merchantId(),consumer().accountId());
        try {assertThat(request(HttpMethod.GET,"/api/v1/auth/me",null,a.accessToken()).getStatusCode().value()).isEqualTo(503);}
        finally {reset(consumerMapper);}
    }
}
