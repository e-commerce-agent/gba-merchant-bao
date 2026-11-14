package com.gba.merchantbao.test.chat;

import com.gba.merchantbao.chat.entity.ChatOwner;
import com.gba.merchantbao.chat.mapper.ChatMessageMapper;
import com.gba.merchantbao.chat.vo.ChatMessageView;
import com.gba.merchantbao.test.support.DockerRequired;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class ChatToolSummaryMigrationIT {
    @Test void v10UpgradeKeepsOldRowsAndMapsNullSummariesToEmptyArrays() throws Exception {
        DockerRequired.check();
        try(var mysql=new MySQLContainer<>("mysql:8.0.34").withReuse(false)) {
            mysql.start();
            var data=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var jdbc=new JdbcTemplate(data);
            var old=Flyway.configure().dataSource(data).locations("classpath:db/migration").target("10").load();
            old.migrate();
            var checksums=jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            jdbc.update("INSERT INTO merchant(id,merchant_code,merchant_name) VALUES(1,'TOOL_MIGRATION','A')");
            jdbc.update("INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,created_at,updated_at) "+
                    "VALUES(10,1,100,'CONSUMER',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            jdbc.update("INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,content,status,run_id,created_at,completed_at) "+
                    "VALUES(11,1,10,1,'ASSISTANT','old answer','COMPLETED',12,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            var latest=Flyway.configure().dataSource(data).locations("classpath:db/migration").load();
            assertThat(latest.migrate().migrationsExecuted).isOne();
            latest.validate();
            assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version<>'11' ORDER BY installed_rank")).isEqualTo(checksums);
            assertThat(jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='chat_message' AND column_name='tool_calls_json'",String.class)).isEqualTo("json");
            assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='chat_message' AND column_name='tool_calls_json'",String.class)).isEqualTo("YES");

            var factory=new SqlSessionFactoryBean();
            factory.setDataSource(data);
            var configuration=new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new ClassPathResource("mapper/chat/ChatMessageMapper.xml"));
            try(var sql=factory.getObject().openSession()) {
                var row=sql.getMapper(ChatMessageMapper.class).findOwned(new ChatOwner(1,100,"CONSUMER"),10,11);
                assertThat(row.content()).isEqualTo("old answer");
                assertThat(row.toolCallsJson()).isNull();
                assertThat(ChatMessageView.from(row).toolCalls()).isEmpty();
                jdbc.update("UPDATE chat_message SET tool_calls_json='[]' WHERE id=11");
                assertThat(ChatMessageView.from(sql.getMapper(ChatMessageMapper.class)
                        .findOwned(new ChatOwner(1,100,"CONSUMER"),10,11)).toolCalls()).isEmpty();
            }
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
