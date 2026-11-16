package com.gba.merchantbao.test.auth;

import com.gba.merchantbao.test.support.DockerRequired;

import com.gba.merchantbao.auth.mapper.AuthTenantMapper;

import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.auth.mapper.ConsumerAccountMapper;
import com.gba.merchantbao.auth.entity.ConsumerAccountRow;
import com.gba.merchantbao.auth.mapper.StaffAccountMapper;
import com.gba.merchantbao.auth.entity.StaffAccountRow;
import com.gba.merchantbao.common.id.IdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthPersistenceIT {
    static { DockerRequired.check(); }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.34")
            .withDatabaseName("auth_mapper_test").withUsername("test").withPassword("test").withReuse(false);

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired TenantResolver tenants;
    @Autowired ConsumerAccountMapper consumers;
    @Autowired StaffAccountMapper staff;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sessions;

    @Test
    @Sql("/db/fixture/auth_accounts.sql")
    void explicitFixtureUsesStableTenantCodesAndIsolatesResourcesAndUniqueIdentifiers() {
        long a = tenant("M_3C_DEMO"), b = tenant("M_ISOLATION_TEST");
        var first = consumers.findByPhone(a, "+8613900000401");
        var second = consumers.findByPhone(a, "+8613900000402");
        var other = consumers.findByPhone(b, "+8613900000401");
        assertThat(first.id()).isNotEqualTo(second.id()).isNotEqualTo(other.id());
        assertThat(first.merchantId()).isEqualTo(a);
        assertThat(other.merchantId()).isEqualTo(b);
        assertThat(consumers.findOwnedById(a, first.id(), first.id())).isNotNull();
        assertThat(consumers.findOwnedById(a, second.id(), first.id())).isNull();
        assertThat(consumers.findOwnedById(b, first.id(), first.id())).isNull();
        var employee = staff.findByUsername(a, "fixture_staff");
        var otherEmployee = staff.findByPhone(b, "+8613900000301");
        assertThat(employee.id()).isNotEqualTo(otherEmployee.id());
        assertThat(staff.findById(b, employee.id())).isNull();
        assertThat(employee.passwordHash()).startsWith("$2b$12$");
        assertThatThrownBy(() -> consumers.insert(consumer(ids.nextId(), a, first.phone())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> staff.insert(employee(ids.nextId(), a, "fixture_staff", "+8613900000302", employee.id())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> staff.insert(employee(ids.nextId(), a, "fixture_other", employee.phone(), employee.id())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @AfterTransaction
    void fixtureRowsDoNotSurviveRollback() {
        assertThat(jdbc.queryForList("SELECT id FROM consumer_account WHERE id IN (2000000000000201,2000000000000202,2000000000000203)"))
                .isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM merchant_staff_account WHERE id IN (2000000000000101,2000000000000102)"))
                .isEmpty();
    }

    @Test
    void tenantResolverRequiresAnActiveLocatedTenantAndBothMapperTreesLoad() {
        var tenant = tenants.resolveActive("M_3C_DEMO").orElseThrow();
        assertThat(tenants.resolveActive(null)).isEmpty();
        assertThat(tenants.resolveActive(" ")).isEmpty();
        assertThat(tenants.resolveActive("M_UNKNOWN")).isEmpty();
        jdbc.update("UPDATE merchant SET status='DISABLED' WHERE id=?", tenant.id());
        assertThat(tenants.resolveActive("M_3C_DEMO")).isEmpty();
        assertThat(sessions.getConfiguration().hasStatement(
                "com.gba.merchantbao.legacy.mapper.EmployeeMapper.update")).isTrue();
        assertThat(sessions.getConfiguration().hasStatement(
                ConsumerAccountMapper.class.getName() + ".findByPhone")).isTrue();
    }

    @Test
    void consumerQueriesAndWritesStayInTenantAndOwnerScope() throws Exception {
        long a = tenant("M_3C_DEMO"), b = tenant("M_ISOLATION_TEST");
        long one = ids.nextId(), two = ids.nextId();
        consumers.insert(consumer(one, a, "+8613900000101"));
        consumers.insert(consumer(two, b, "+8613900000101"));
        assertThat(consumers.findByPhone(a, "+8613900000101").id()).isEqualTo(one);
        assertThat(consumers.findByPhone(b, "+8613900000101").id()).isEqualTo(two);
        assertThat(consumers.findById(b, one)).isNull();
        assertThat(consumers.findOwnedById(a, one, two)).isNull();
        assertThat(consumers.findOwnedById(a, one, one).id()).isEqualTo(one);
        assertThat(consumers.updateDisplayName(b, one, "wrong tenant", 0)).isZero();
        assertThat(consumers.delete(b, one, 0)).isZero();
        assertThat(consumers.updateDisplayName(a, one, "updated", 0)).isOne();
        assertThat(consumers.updateDisplayName(a, one, "stale", 0)).isZero();
        assertThat(consumers.findById(a, one).version()).isOne();
        assertThat(consumers.findById(a, one).updatedAt()).isNotNull();
        assertThat(consumers.delete(a, one, 0)).isZero();
        assertThat(consumers.delete(a, one, 1)).isOne();
        assertThat(consumers.findById(a, one)).isNull();
        assertThat(consumers.findById(b, two)).isNotNull();
        assertIndex(ConsumerAccountMapper.class, "findByPhone", Map.of("merchantId", b, "phone", "+8613900000101"),
                "uk_consumer_merchant_phone");
        assertIndex(ConsumerAccountMapper.class, "findById", Map.of("merchantId", b, "accountId", two),
                "PRIMARY", "uk_consumer_merchant_id");
    }

    @Test
    void staffIdentifiersUseSeparateIndexesAndCreatorWritesCannotCrossTenants() throws Exception {
        long a = tenant("M_3C_DEMO"), b = tenant("M_ISOLATION_TEST");
        long one = ids.nextId(), two = ids.nextId(), child = ids.nextId();
        staff.insert(employee(one, a, "test_staff", "+8613900000201", null));
        staff.insert(employee(two, b, "test_staff", "+8613900000201", null));
        staff.insert(employee(child, a, "test_child", "+8613900000202", one));
        assertThat(staff.findByUsername(a, "test_staff").id()).isEqualTo(one);
        assertThat(staff.findByPhone(a, "+8613900000201").id()).isEqualTo(one);
        assertThat(staff.findByUsername(b, "test_staff").id()).isEqualTo(two);
        assertThat(staff.findById(a, child).createdBy()).isEqualTo(one);
        assertThat(staff.findById(a, one).createdBy()).isNull();
        assertThat(staff.findById(b, one)).isNull();
        assertThat(staff.updateDisplayName(b, one, "wrong tenant", 0)).isZero();
        assertThat(staff.delete(b, one, 0)).isZero();
        assertThat(staff.updateDisplayName(a, one, "updated", 0)).isOne();
        assertThat(staff.updateDisplayName(a, one, "stale", 0)).isZero();
        assertThat(staff.findById(a, one).version()).isOne();
        assertThatThrownBy(() -> staff.insert(employee(ids.nextId(), b, "bad_creator", "+8613900000203", one)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertIndex(StaffAccountMapper.class, "findByUsername", Map.of("merchantId", a, "username", "test_staff"),
                "uk_staff_merchant_username");
        assertIndex(StaffAccountMapper.class, "findByPhone", Map.of("merchantId", a, "phone", "+8613900000201"),
                "uk_staff_merchant_phone");
        assertIndex(StaffAccountMapper.class, "findById", Map.of("merchantId", a, "accountId", one),
                "PRIMARY", "uk_staff_merchant_id");
        assertIndex(com.gba.merchantbao.auth.mapper.AuthTenantMapper.class,
                "findActiveByCode", Map.of("merchantCode", "M_3C_DEMO"), "uk_merchant_code");
        assertThat(staff.delete(a, child, 0)).isOne();
        assertThat(staff.delete(a, one, 1)).isOne();
        assertThat(staff.findById(b, two)).isNotNull();
    }

    // EXPLAIN the actual bound Mapper SQL, not a separately maintained imitation query.
    private void assertIndex(Class<?> mapper, String method, Map<String, Object> parameters, String... indexes) throws Exception {
        var bound = sessions.getConfiguration().getMappedStatement(mapper.getName() + "." + method).getBoundSql(parameters);
        var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource());
        try (var statement = connection.prepareStatement("EXPLAIN " + bound.getSql())) {
            int i = 1;
            for (var mapping : bound.getParameterMappings()) statement.setObject(i++, parameters.get(mapping.getProperty()));
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("key")).isIn(indexes);
                assertThat(rows.getString("type")).isIn("const", "ref", "eq_ref");
            }
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, jdbc.getDataSource());
        }
    }

    private long tenant(String code) { return tenants.resolveActive(code).orElseThrow().id(); }
    private ConsumerAccountRow consumer(long id, long merchant, String phone) {
        return new ConsumerAccountRow(id, merchant, phone, "test consumer", "ACTIVE", null, null, 0);
    }
    private StaffAccountRow employee(long id, long merchant, String username, String phone, Long creator) {
        return new StaffAccountRow(id, merchant, username, phone, "test staff", "test-only-digest", "ACTIVE", null, null, creator, 0);
    }
}
