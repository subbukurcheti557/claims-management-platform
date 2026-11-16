package com.icmp.claims.support;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real S3-compatible object storage (SeaweedFS, the same image as docker-compose) for integration tests, so
 * uploads and downloads go through the production storage code.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ObjectStorageTestConfig {

    public static final String IMAGE = "chrislusf/seaweedfs:3.97";
    private static final int S3_PORT = 8333;

    @Bean(destroyMethod = "stop")
    GenericContainer<?> objectStorageContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs-s3.json"),
                        "/etc/seaweedfs/s3.json")
                .withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT,
                        "-s3.config=/etc/seaweedfs/s3.json")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forListeningPorts(S3_PORT).withStartupTimeout(Duration.ofSeconds(90)));
        container.start();
        return container;
    }

    @Bean
    DynamicPropertyRegistrar objectStorageProperties(GenericContainer<?> objectStorageContainer) {
        return registry -> {
            registry.add("app.documents.storage.endpoint", () -> "http://" + objectStorageContainer.getHost() + ":"
                    + objectStorageContainer.getMappedPort(S3_PORT));
            registry.add("app.documents.storage.access-key", () -> "claims-dev-access-key");
            registry.add("app.documents.storage.secret-key", () -> "claims-dev-secret-key-change-me");
        };
    }
}
