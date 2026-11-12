package com.gba.merchantbao.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.common.id.SnowflakeIdGenerator;
import com.gba.merchantbao.common.id.IdGenerator;
import com.gba.merchantbao.config.IdConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {
    @Test
    void concurrentCallsRemainUniqueAndDifferentWorkersDoNotCollide() throws Exception {
        MutableClock clock = new MutableClock();
        var generator = new SnowflakeIdGenerator(7, clock);
        var ids = ConcurrentHashMap.<Long>newKeySet();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 8).mapToObj(worker -> pool.submit(() -> {
                for (int i = 0; i < 400; i++) assertThat(ids.add(generator.nextId())).isTrue();
            })).toList();
            for (var task : tasks) task.get();
        }
        assertThat(ids).hasSize(3200).allMatch(id -> id > 9_007_199_254_740_991L);
        assertThat(ids).doesNotContain(new SnowflakeIdGenerator(8, clock).nextId());
    }

    @Test
    void twoApplicationWorkersGenerateConcurrentlyAtTheSameMillisecondWithoutDuplicates() throws Exception {
        var clock = new MutableClock();
        var generators = new SnowflakeIdGenerator[]{new SnowflakeIdGenerator(7, clock), new SnowflakeIdGenerator(8, clock)};
        var ids = ConcurrentHashMap.<Long>newKeySet();
        var start = new CountDownLatch(1);
        long fixedMillis = clock.millis();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 8).mapToObj(thread -> pool.submit(() -> {
                start.await();
                for (int i = 0; i < 800; i++) assertThat(ids.add(generators[thread % 2].nextId())).isTrue();
                return null;
            })).toList();
            start.countDown();
            for (var task : tasks) task.get(30, TimeUnit.SECONDS);
        }
        assertThat(clock.millis()).isEqualTo(fixedMillis);
        assertThat(ids).hasSize(6400);
        for (int worker : new int[]{7, 8}) {
            assertThat(ids.stream().filter(id -> ((id >>> 12) & 1023) == worker).count()).isEqualTo(3200);
        }
    }

    @Test
    void rollbackIsRejectedUntilClockRecovers() {
        MutableClock clock = new MutableClock();
        var generator = new SnowflakeIdGenerator(0, clock);
        long first = generator.nextId();
        clock.millis.decrementAndGet();
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class).hasMessageContaining("backwards");
        clock.millis.incrementAndGet();
        assertThat(generator.nextId()).isGreaterThan(first);
    }

    @Test
    void sequenceExhaustionCannotWrapAndEpochCannotOverflow() {
        MutableClock clock = new MutableClock();
        var generator = new SnowflakeIdGenerator(0, clock);
        for (int i = 0; i < 4096; i++) generator.nextId();
        assertThatThrownBy(generator::nextId).hasMessageContaining("exhausted");
        clock.millis.incrementAndGet();
        assertThat(generator.nextId()).isPositive();
        clock.millis.set(SnowflakeIdGenerator.EPOCH_MILLIS - 1);
        assertThatThrownBy(generator::nextId).hasMessageContaining("epoch");
        clock.millis.set(SnowflakeIdGenerator.EPOCH_MILLIS + (1L << 41));
        assertThatThrownBy(generator::nextId).hasMessageContaining("epoch");
    }

    @Test
    void apiIdsSerializeAsDecimalStringsWithoutChangingOtherNumbers() throws Exception {
        long id = new SnowflakeIdGenerator(7, new MutableClock()).nextId();
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(Map.of("accountId", new ApiId(id),
                "merchantId", new ApiId(Long.MAX_VALUE), "expiresIn", 7200)));
        assertThat(json.get("accountId").isTextual()).isTrue();
        assertThat(json.get("accountId").asText()).isEqualTo(Long.toString(id));
        assertThat(json.get("merchantId").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(json.get("expiresIn").isNumber()).isTrue();
    }

    @Test
    void workerConfigurationIsRequiredAndRangeChecked() {
        var runner = new ApplicationContextRunner().withUserConfiguration(IdConfiguration.class);
        runner.run(context -> assertThat(context).hasFailed());
        for (int invalid : new int[]{-1, 1024}) {
            runner.withPropertyValues("merchant.id.worker-id=" + invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("merchant.id.worker-id=1023")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdGenerator.class);
                    assertThat(context.getBean(IdGenerator.class)).isSameAs(context.getBean(IdGenerator.class));
                });
    }

    private static class MutableClock extends Clock {
        final AtomicLong millis = new AtomicLong(Instant.parse("2026-11-05T00:00:00Z").toEpochMilli());
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis()); }
        public long millis() { return millis.get(); }
    }
}
