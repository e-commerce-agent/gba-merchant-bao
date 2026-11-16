package com.gba.merchantbao.test.foundation;

import org.junit.jupiter.api.Test;
import java.util.jar.JarFile;
import static org.assertj.core.api.Assertions.assertThat;

class JwtPackagingIT {
    @Test
    void deployableJarExcludesActualDevelopmentAndTestProfiles() throws Exception {
        try (var jar = new JarFile("target/gba-merchant-bao-1.0-SNAPSHOT.jar")) {
            assertThat(jar.getEntry("BOOT-INF/classes/application.yml")).isNotNull();
            assertThat(jar.getEntry("BOOT-INF/classes/application-prod.yml")).isNotNull();
            assertThat(jar.stream().map(java.util.zip.ZipEntry::getName))
                    .noneMatch(name -> name.endsWith("/application-dev.yml") || name.endsWith("/application-dev.yaml")
                            || name.endsWith("/application-test.yml") || name.endsWith("/application-test.yaml"));
        }
    }
}
