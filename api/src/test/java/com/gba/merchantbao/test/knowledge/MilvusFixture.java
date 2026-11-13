package com.gba.merchantbao.test.knowledge;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import java.time.Duration;

/** Disposable network and storage; never connects to a developer's Compose project. */
final class MilvusFixture implements AutoCloseable {
    final GenericContainer<?> milvus = new GenericContainer<>("milvusdb/milvus:v2.5.9")
            .withEnv("ETCD_USE_EMBED", "true").withEnv("ETCD_DATA_DIR", "/var/lib/milvus/etcd")
            .withEnv("COMMON_STORAGETYPE", "local")
            .withCommand("milvus", "run", "standalone").withExposedPorts(19530, 9091)
            .waitingFor(Wait.forHttp("/healthz").forPort(9091).withStartupTimeout(Duration.ofMinutes(3)));
    void start() { milvus.start(); }
    String uri() { return "http://" + milvus.getHost() + ":" + milvus.getMappedPort(19530); }
    @Override public void close() { milvus.close(); }
}
