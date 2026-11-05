package io.github.vanilds.creditdesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL 16 in Docker for integration tests — same version as production, so Flyway migrations,
 * partial unique indexes and pg_trgm are tested for real (H2 would not support them).
 * {@code @ServiceConnection} points the datasource at the container; no URL to configure.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
