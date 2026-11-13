package com.gba.merchantbao.test.support;

import org.testcontainers.DockerClientFactory;

public final class DockerRequired {
    private DockerRequired() {
    }

    public static void check() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException(
                    "Docker is required. Start Docker Desktop with the Linux container engine, "
                            + "then rerun mvn -f api/pom.xml clean verify. "
                            + "Integration tests are required and cannot be skipped.");
        }
    }
}
