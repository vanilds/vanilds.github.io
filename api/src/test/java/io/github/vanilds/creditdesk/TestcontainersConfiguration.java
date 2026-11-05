package io.github.vanilds.creditdesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Real infrastructure in Docker for integration tests, the same as {@code compose.yaml}:
 * <ul>
 *   <li>PostgreSQL 16 — Flyway migrations, partial unique indexes and pg_trgm are tested for real (H2 would not
 *   support them). {@code @ServiceConnection} points the datasource at the container; no URL to configure.</li>
 *   <li>RabbitMQ 4.1 — publisher confirms, dead-lettering and the x-death header are broker features: a mock would
 *   only test what we believe the broker does.</li>
 *   <li>SeaweedFS — an S3-compatible server, so presigned URLs are signed and checked like on Amazon S3.</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String S3_ACCESS_KEY = "test";
    static final String S3_SECRET_KEY = "test-secret";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer(DockerImageName.parse("public.ecr.aws/docker/library/rabbitmq:4.1-management-alpine")
                .asCompatibleSubstituteFor("rabbitmq"));
    }

    @Bean
    GenericContainer<?> seaweedfs() {
        return new GenericContainer<>("chrislusf/seaweedfs:4.50")
                .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json",
                        "-master.volumeSizeLimitMB=64", "-volume.max=10")
                .withCopyToContainer(Transferable.of("""
                        {"identities": [{"name": "test", "credentials": [{"accessKey": "%s", "secretKey": "%s"}],
                          "actions": ["Admin", "Read", "Write", "List", "Tagging"]}]}
                        """.formatted(S3_ACCESS_KEY, S3_SECRET_KEY)), "/etc/seaweedfs/s3.json")
                .withExposedPorts(8333)
                .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedfs) {
        return registry -> {
            registry.add("creditdesk.storage.endpoint",
                    () -> "http://%s:%d".formatted(seaweedfs.getHost(), seaweedfs.getMappedPort(8333)));
            registry.add("creditdesk.storage.access-key", () -> S3_ACCESS_KEY);
            registry.add("creditdesk.storage.secret-key", () -> S3_SECRET_KEY);
        };
    }
}
