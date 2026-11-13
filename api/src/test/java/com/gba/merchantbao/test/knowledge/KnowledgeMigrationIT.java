package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.test.support.DockerRequired;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class KnowledgeMigrationIT {
    @Test void upgradePreservesOldMessagesAndEnforcesCompositeOwnership() {
        DockerRequired.check();
        try (var mysql = new MySQLContainer<>("mysql:8.0.34").withReuse(false)) {
            mysql.start();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration").target("9").load().migrate();
            jdbc.update("INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(1,'RAG_A','A'),(2,'RAG_B','B')");
            jdbc.update("INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,created_at,updated_at) "
                    + "VALUES(10,1,100,'MERCHANT_STAFF',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            jdbc.update("INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,content,status,run_id,created_at,completed_at) "
                    + "VALUES(11,1,10,1,'ASSISTANT','old answer','COMPLETED',12,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            var latest = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration").load();
            assertThat(latest.migrate().migrationsExecuted).isOne(); latest.validate();
            assertThat(jdbc.queryForObject("SELECT content FROM chat_message WHERE id=11", String.class)).isEqualTo("old answer");
            assertThat(jdbc.queryForObject("SELECT sources_json FROM chat_message WHERE id=11", String.class)).isNull();
            // Storage compatibility only: task 2 will map NULL to [] in actual chat responses.
            assertThat(jdbc.queryForObject("SELECT CAST(COALESCE(sources_json,JSON_ARRAY()) AS CHAR) FROM chat_message WHERE id=11", String.class)).isEqualTo("[]");
            jdbc.update("INSERT INTO knowledge_document VALUES(20,1,'政策','PROCESSING',1,100,UTC_TIMESTAMP(3))");
            jdbc.update("INSERT INTO knowledge_chunk VALUES(21,1,20,1,'正文',UTC_TIMESTAMP(3))");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO knowledge_chunk VALUES(22,2,20,1,'跨商户',UTC_TIMESTAMP(3))"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO knowledge_chunk VALUES(23,1,20,1,'重复序号',UTC_TIMESTAMP(3))"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE knowledge_document SET status='OTHER' WHERE id=20"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasRootCauseMessage("Check constraint 'ck_knowledge_document_status' is violated.");
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
