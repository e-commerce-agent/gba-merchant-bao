package com.gba.merchantbao.test;

import com.gba.merchantbao.legacy.config.RedisConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisIT {
    static {
        DockerRequired.check();
    }

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withReuse(false);

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> redis;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void connectToIsolatedContainer() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new RedisConfiguration().redisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void closeConnection() {
        if (factory != null) factory.destroy();
    }

    @Test
    void stringValuesRoundTripWithExpiryAndConditionalWrite() {
        String key = key();
        redis.opsForValue().set(key, "value", Duration.ofMinutes(1));
        assertThat(redis.opsForValue().get(key)).isEqualTo("value");
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
        assertThat(redis.opsForValue().setIfAbsent(key, "replacement")).isFalse();
        assertThat(redis.opsForValue().get(key)).isEqualTo("value");
    }

    @Test
    void hashFieldsCanBeReadAndDeleted() {
        String key = key();
        redis.opsForHash().put(key, "name", "merchant");
        assertThat(redis.opsForHash().get(key, "name")).isEqualTo("merchant");
        assertThat(redis.opsForHash().delete(key, "name")).isEqualTo(1);
        assertThat(redis.opsForHash().hasKey(key, "name")).isFalse();
    }

    @Test
    void listPreservesOrderAndSupportsPop() {
        String key = key();
        redis.opsForList().rightPushAll(key, "a", "b", "c");
        assertThat(redis.opsForList().range(key, 0, -1)).containsExactly("a", "b", "c");
        assertThat(redis.opsForList().rightPop(key)).isEqualTo("c");
        assertThat(redis.opsForList().size(key)).isEqualTo(2);
    }

    @Test
    void setDeduplicatesMembers() {
        String key = key();
        assertThat(redis.opsForSet().add(key, "a", "a", "b")).isEqualTo(2);
        assertThat(redis.opsForSet().members(key)).containsExactlyInAnyOrder("a", "b");
        assertThat(redis.opsForSet().remove(key, "a")).isEqualTo(1);
    }

    @Test
    void sortedSetOrdersMembersAndUpdatesScore() {
        String key = key();
        redis.opsForZSet().add(key, "low", 1);
        redis.opsForZSet().add(key, "high", 2);
        assertThat(redis.opsForZSet().range(key, 0, -1)).containsExactly("low", "high");
        assertThat(redis.opsForZSet().incrementScore(key, "low", 3)).isEqualTo(4);
        assertThat(redis.opsForZSet().range(key, 0, -1)).containsExactly("high", "low");
    }

    @Test
    void keysExposeTypeAndCanBeDeleted() {
        String key = key();
        redis.opsForValue().set(key, "value");
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.type(key)).isEqualTo(DataType.STRING);
        assertThat(redis.delete(key)).isTrue();
        assertThat(redis.hasKey(key)).isFalse();
    }

    private String key() {
        return "merchantbao:test:" + UUID.randomUUID();
    }
}
