package com.gba.merchantbao.test.chat;

import com.gba.merchantbao.test.support.DockerRequired;

import com.gba.merchantbao.chat.entity.ChatOwner;
import com.gba.merchantbao.chat.entity.ChatSessionRow;
import com.gba.merchantbao.chat.entity.ChatMessageRow;
import com.gba.merchantbao.chat.mapper.ChatSessionMapper;
import com.gba.merchantbao.chat.mapper.ChatMessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.data.redis.host=127.0.0.1","spring.data.redis.port=1"})
@ActiveProfiles("test") @Testcontainers @Transactional
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ChatPersistenceIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("chat_mapper").withUsername("test").withPassword("test").withReuse(false);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl); r.add("spring.datasource.username",MYSQL::getUsername);
        r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired ChatSessionMapper sessions;
    @Autowired ChatMessageMapper messages;
    @Autowired JdbcTemplate jdbc;
    static final LocalDateTime NOW = LocalDateTime.of(2026,11,8,0,0,0,123_000_000);

    @Test void ownedSessionsHaveStableCursorPagesAndCannotBeReadOrArchivedByOthers() {
        var owner = owner("M_3C_DEMO",101,"CONSUMER");
        var stranger = owner("M_3C_DEMO",102,"CONSUMER");
        var otherTenant = owner("M_ISOLATION_TEST",101,"CONSUMER");
        var otherRole = owner("M_3C_DEMO",101,"MERCHANT_STAFF");
        assertThat(sessions.insert(session(501,owner,NOW))).isOne();
        sessions.insert(session(502,owner,NOW)); sessions.insert(session(503,owner,NOW.minusSeconds(1)));
        sessions.insert(session(504,stranger,NOW)); sessions.insert(session(505,otherTenant,NOW));
        sessions.insert(session(506,otherRole,NOW));
        var first = sessions.pageOwned(owner,null,null,1);
        assertThat(first).extracting(ChatSessionRow::id).containsExactly(502L);
        var second = sessions.pageOwned(owner,first.getFirst().updatedAt(),first.getFirst().id(),1);
        assertThat(second).extracting(ChatSessionRow::id).containsExactly(501L);
        assertThat(sessions.pageOwned(owner,second.getFirst().updatedAt(),second.getFirst().id(),100))
                .extracting(ChatSessionRow::id).containsExactly(503L);
        assertThat(sessions.pageOwned(owner,NOW.minusSeconds(1),503L,100)).isEmpty();
        assertThat(sessions.findOwned(owner,501).createdAt()).isEqualTo(NOW);
        assertThat(sessions.lockOwned(owner,501).id()).isEqualTo(501);
        for (var wrong : new ChatOwner[]{stranger,otherTenant,otherRole}) {
            assertThat(sessions.findOwned(wrong,501)).isNull(); assertThat(sessions.lockOwned(wrong,501)).isNull();
            assertThat(sessions.archiveOwned(wrong,501,NOW.plusSeconds(1))).isZero();
        }
        assertThat(sessions.archiveOwned(owner,501,NOW.plusSeconds(1))).isOne();
        assertThat(sessions.archiveOwned(owner,501,NOW.plusSeconds(2))).isZero();
        var archived = sessions.findOwned(owner,501);
        assertThat(archived.status()).isEqualTo("ARCHIVED"); assertThat(archived.archivedBy()).isEqualTo(101);
        assertThat(archived.archivedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(sessions.findOwned(otherTenant,505).status()).isEqualTo("ACTIVE");
    }
    @Test void messagesAndConditionalRunWritesAreScopedOrderedAndRejectLateCallbacks() {
        var owner = owner("M_3C_DEMO",101,"CONSUMER");
        var strangers = new ChatOwner[]{owner("M_3C_DEMO",102,"CONSUMER"),owner("M_ISOLATION_TEST",101,"CONSUMER"),owner("M_3C_DEMO",101,"MERCHANT_STAFF")};
        sessions.insert(session(501,owner,NOW));
        for (var wrong : strangers) assertThat(sessions.acquireRun(wrong,501,1,301,NOW.plusSeconds(30),NOW)).isZero();
        assertThat(sessions.acquireRun(owner,501,2,301,NOW.plusSeconds(30),NOW)).isZero();
        assertThat(sessions.acquireRun(owner,501,1,301,NOW,NOW)).isZero();
        assertThat(sessions.acquireRun(owner,501,1,301,NOW.plusSeconds(30),NOW)).isOne();
        assertThat(sessions.findOwned(owner,501).nextMessageSeq()).isEqualTo(3);
        assertThat(sessions.acquireRun(owner,501,3,302,NOW.plusSeconds(30),NOW)).isZero();
        assertThat(sessions.archiveOwned(owner,501,NOW)).isZero();
        var assistant = message(602,owner,2,"ASSISTANT",301);
        var user = message(601,owner,1,"USER",301);
        for (var wrong : strangers) assertThat(messages.insertOwned(wrong,user)).isZero();
        assertThat(messages.insertOwned(owner,assistant)).isOne(); assertThat(messages.insertOwned(owner,user)).isOne();
        assertThat(messages.pageOwned(owner,501,0,1)).extracting(ChatMessageRow::id).containsExactly(601L);
        assertThat(messages.pageOwned(owner,501,1,100)).extracting(ChatMessageRow::id).containsExactly(602L);
        assertThat(messages.pageOwned(owner,501,2,100)).isEmpty();
        assertThat(messages.findUserByClientId(owner,501,user.clientMessageId()).content()).isEqualTo(" 原文 \n");
        assertThat(messages.findOwned(owner,501,602).inputTokens()).isNull();
        for (var wrong : strangers) {
            assertThat(messages.pageOwned(wrong,501,0,100)).isEmpty();
            assertThat(messages.findOwned(wrong,501,602)).isNull();
            assertThat(messages.findUserByClientId(wrong,501,user.clientMessageId())).isNull();
            assertThat(messages.finishOwned(wrong,501,602,301,"COMPLETED","bad",null,1L,2L,NOW.plusSeconds(1))).isZero();
            assertThat(sessions.releaseRun(wrong,501,301,NOW.plusSeconds(1))).isZero();
        }
        assertThat(sessions.releaseRun(owner,501,301,NOW)).isZero();
        assertThat(messages.finishOwned(owner,999,602,301,"COMPLETED","bad",null,null,null,NOW.plusSeconds(1))).isZero();
        assertThat(messages.finishOwned(owner,501,602,999,"COMPLETED","bad",null,null,null,NOW.plusSeconds(1))).isZero();
        assertThat(messages.finishOwned(owner,501,602,301,"TIMED_OUT","", "AGENT_TIMEOUT",null,null,NOW.plusSeconds(1))).isZero();
        assertThat(messages.finishOwned(owner,501,602,301,"COMPLETED","准确结果",null,10L,3L,NOW.plusSeconds(1))).isOne();
        var completed = messages.findOwned(owner,501,602);
        assertThat(completed.content()).isEqualTo("准确结果"); assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.inputTokens()).isEqualTo(10); assertThat(completed.completedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(messages.finishOwned(owner,501,602,301,"FAILED","bad","ERR",null,null,NOW.plusSeconds(2))).isZero();
        assertThat(sessions.releaseRun(owner,501,301,NOW.plusSeconds(1))).isOne();
        assertThat(sessions.acquireRun(owner,501,3,302,NOW.plusSeconds(30),NOW.plusSeconds(2))).isOne();
        assertThat(sessions.releaseRun(owner,501,301,NOW.plusSeconds(3))).isZero();
        assertThat(messages.insertOwned(owner,message(604,owner,4,"ASSISTANT",302))).isOne();
        assertThat(messages.finishOwned(owner,501,604,302,"COMPLETED","late",null,null,null,NOW.plusSeconds(30))).isZero();
        assertThat(messages.finishOwned(owner,501,604,302,"TIMED_OUT","部分","AGENT_TIMEOUT",null,null,NOW.plusSeconds(30))).isOne();
        assertThat(sessions.releaseRun(owner,501,302,NOW.plusSeconds(30))).isOne();
        assertThat(sessions.findOwned(owner,501).activeRunId()).isNull();
    }
    @Test @Sql({"/db/fixture/auth_accounts.sql","/db/fixture/chat_conversations.sql"})
    void explicitFixturesHaveTwoTenantsMultipleOwnersAndCompleteHistoryWithoutDevData() {
        var a = owner("M_3C_DEMO",2000000000000201L,"CONSUMER");
        var b = owner("M_3C_DEMO",2000000000000202L,"CONSUMER");
        var staff = owner("M_3C_DEMO",2000000000000101L,"MERCHANT_STAFF");
        var isolated = owner("M_ISOLATION_TEST",2000000000000203L,"CONSUMER");
        assertThat(sessions.pageOwned(a,null,null,100)).extracting(ChatSessionRow::id).containsExactly(2200000000000101L);
        assertThat(sessions.pageOwned(b,null,null,100)).extracting(ChatSessionRow::id).containsExactly(2200000000000102L);
        assertThat(sessions.pageOwned(staff,null,null,100)).extracting(ChatSessionRow::id).containsExactly(2200000000000103L);
        assertThat(sessions.pageOwned(isolated,null,null,100)).extracting(ChatSessionRow::id).containsExactly(2200000000000104L);
        assertThat(messages.pageOwned(a,2200000000000101L,0,100)).extracting(ChatMessageRow::content)
                .containsExactly("Fixture user one","Fixture assistant one");
        assertThat(messages.pageOwned(staff,2200000000000101L,0,100)).isEmpty();
        assertThat(messages.pageOwned(isolated,2200000000000101L,0,100)).isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM chat_session WHERE id BETWEEN 2200000000000001 AND 2200000000000004")).isEmpty();
        assertThat(jdbc.queryForList("SELECT script FROM flyway_schema_history WHERE script='R__030_chat_demo.sql'")).isEmpty();
    }
    @AfterTransaction void fixtureRollsBackByBusinessIdentity() {
        assertThat(jdbc.queryForList("SELECT id FROM chat_session WHERE id BETWEEN 2200000000000101 AND 2200000000000104")).isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM chat_message WHERE id BETWEEN 2300000000000101 AND 2300000000000108")).isEmpty();
    }
    ChatMessageRow message(long id,ChatOwner owner,long seq,String sender,long run) {
        boolean user = sender.equals("USER");
        return new ChatMessageRow(id,owner.merchantId(),501,seq,sender,user ? owner.accountId() : null,
                user ? "00000000-0000-4000-8000-000000000001" : null,user ? " 原文 \n" : "",user ? "COMPLETED" : "STARTED",run,null,
                user ? null : "v1",user ? null : "compatible",user ? null : "test-model",null,null,NOW,user ? NOW : null);
    }
    ChatOwner owner(String code,long account,String role) {
        return new ChatOwner(jdbc.queryForObject("SELECT id FROM merchant WHERE merchant_code=?",Long.class,code),account,role);
    }
    ChatSessionRow session(long id,ChatOwner owner,LocalDateTime time) {
        return new ChatSessionRow(id,owner.merchantId(),owner.accountId(),owner.role(),"新对话","ACTIVE",time,time,null,null,1,null,null);
    }
}
