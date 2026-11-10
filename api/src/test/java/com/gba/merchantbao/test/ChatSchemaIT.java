package com.gba.merchantbao.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ChatSchemaIT {
    static { DockerRequired.check(); }
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("chat_schema").withUsername("test").withPassword("test").withReuse(false);

    @Test void emptyDatabaseAndV4UpgradeKeepHistoricalChecksumsAndRows() throws Exception {
        for (boolean upgrade : new boolean[]{false, true}) {
            String url = database();
            var config = Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).locations("classpath:db/migration");
            if (upgrade) config.target("4").load().migrate();
            try (var c = DriverManager.getConnection(url, "root", MYSQL.getPassword())) {
                if (upgrade) sql(c, "INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(91,'PRESERVED','人工名称')");
                var before = upgrade ? history(c) : java.util.Map.<String,Integer>of();
                config.target("latest").load().migrate();
                assertThat(config.load().info().current().getVersion().getVersion()).isEqualTo("8");
                if (upgrade) {
                    assertThat(history(c)).containsAllEntriesOf(before);
                    try (var rs = c.createStatement().executeQuery("SELECT merchant_name FROM merchant WHERE merchant_code='PRESERVED'")) {
                        assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("人工名称");
                    }
                } else sql(c, "INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(91,'NEW','新商户')");
                sql(c, "INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,created_at,updated_at) VALUES(1,91,101,'CONSUMER','2026-01-01','2026-01-01')");
                try (var rs = c.createStatement().executeQuery("SELECT title,status,next_message_seq FROM chat_session WHERE merchant_id=91 AND id=1")) {
                    assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("新对话");
                    assertThat(rs.getString(2)).isEqualTo("ACTIVE"); assertThat(rs.getLong(3)).isEqualTo(1);
                }
            }
        }
    }

    @Test void constraintsKeepTenantIdempotencySequenceAndLegalMessageStates() throws Exception {
        String url = database();
        Flyway.configure().dataSource(url,"root",MYSQL.getPassword()).locations("classpath:db/migration").load().migrate();
        try (var c = DriverManager.getConnection(url,"root",MYSQL.getPassword())) {
            sql(c,"INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(91,'A','A'),(92,'B','B')");
            sql(c,"INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,created_at,updated_at) VALUES(1,91,101,'CONSUMER','2026-01-01','2026-01-01'),(2,92,201,'MERCHANT_STAFF','2026-01-01','2026-01-01'),(3,91,102,'CONSUMER','2026-01-01','2026-01-01')");
            for (String change : new String[]{"status='active'","status='DELETED'","owner_role='consumer'","owner_role='ADMIN'","next_message_seq=0","active_run_id=20","run_deadline_at='2026-01-02'","title=''","archived_at='2026-01-02'","status='ARCHIVED'"}) {
                rejected(c,"UPDATE chat_session SET " + change + " WHERE id=1");
            }
            String user = "INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at) VALUES(%d,%d,%d,%d,'USER',101,'00000000-0000-4000-8000-000000000001',' 原文 ','COMPLETED',%d,'2026-01-01','2026-01-01')";
            sql(c,user.formatted(11,91,1,1,301));
            rejected(c,user.formatted(12,92,1,1,301)); // Composite tenant FK.
            rejected(c,user.formatted(12,91,1,3,302)); // Same client key, different run/seq.
            sql(c,user.formatted(12,91,3,1,301)); // Other session may reuse key.
            sql(c,"INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,content,status,run_id,created_at) VALUES(13,91,1,2,'ASSISTANT','','STARTED',301,'2026-01-01'),(14,91,1,4,'ASSISTANT','','STREAMING',302,'2026-01-01')");
            rejected(c,"INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,content,status,run_id,created_at) VALUES(15,91,1,5,'ASSISTANT','','STARTED',301,'2026-01-01')");
            rejected(c,"UPDATE chat_message SET message_seq=2 WHERE id=14");
            for (String change : new String[]{"status='completed'","status='BOGUS'","status='STARTED',completed_at=NULL","sender_account_id=NULL","client_message_id=NULL","client_message_id='INVALID'","input_tokens=1","error_code='ERROR'"}) {
                rejected(c,"UPDATE chat_message SET " + change + " WHERE id=11");
            }
            for (String change : new String[]{"sender_type='assistant'","sender_account_id=101","client_message_id='00000000-0000-4000-8000-000000000002'","input_tokens=-1","completed_at='2026-01-02'","status='COMPLETED'","error_code='ERROR'"}) {
                rejected(c,"UPDATE chat_message SET " + change + " WHERE id=13");
            }
            for (String status : new String[]{"COMPLETED","CANCELLED","TIMED_OUT","FAILED"}) {
                sql(c,"UPDATE chat_message SET status='"+ status +"',completed_at='2026-01-02',error_code=" + (status.equals("COMPLETED") ? "NULL" : "'AGENT_ERROR'") + " WHERE id=13");
            }
            rejected(c,"UPDATE chat_message SET completed_at='2025-01-01' WHERE id=13");
            rejected(c,"DELETE FROM chat_session WHERE id=1");
            sql(c,"UPDATE chat_session SET status='ARCHIVED',archived_at='2026-01-02',archived_by=101 WHERE id=1");
            rejected(c,"UPDATE chat_session SET active_run_id=300,run_deadline_at='2026-01-02' WHERE id=1");
        }
    }
    private void rejected(Connection c, String statement) {
        assertThatThrownBy(() -> sql(c,statement)).as(statement).isInstanceOf(SQLException.class);
    }

    private java.util.Map<String,Integer> history(Connection c) throws Exception {
        var result = new java.util.LinkedHashMap<String,Integer>();
        try (var rs = c.createStatement().executeQuery("SELECT version,checksum FROM flyway_schema_history WHERE version IN ('1','2','3','4')")) {
            while (rs.next()) result.put(rs.getString(1),rs.getInt(2));
        }
        return result;
    }
    private String database() throws Exception {
        String name = "chat_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword())) { sql(c,"CREATE DATABASE " + name); }
        return MYSQL.getJdbcUrl().replace("/chat_schema", "/" + name);
    }
    private void sql(Connection c, String sql) throws SQLException { try (var s = c.createStatement()) { s.execute(sql); } }
}
