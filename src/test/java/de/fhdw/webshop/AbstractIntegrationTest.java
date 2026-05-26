package de.fhdw.webshop;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that run against a real PostgreSQL database
 * provided by Testcontainers (not H2 or mocks). A single container is started once
 * and reused for the whole JVM test run (singleton-container pattern), which keeps the
 * suite fast. Spring Boot wires the datasource from the container via {@link ServiceConnection},
 * and Flyway applies the real migrations on context startup — so tests exercise the actual
 * schema, including PostgreSQL-specific constructs such as the enum types.
 *
 * <p>Requires a running Docker environment (available locally and on the CI runner).
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES_CONTAINER.start();
    }
}
