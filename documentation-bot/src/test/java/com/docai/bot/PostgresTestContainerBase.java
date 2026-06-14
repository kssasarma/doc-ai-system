package com.docai.bot;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for integration tests that require a real PostgreSQL + pgvector database.
 *
 * Uses a single container (reused across all subclasses in the same JVM) to minimise
 * startup overhead. The pgvector extension is created via the SQL init script defined
 * in the container configuration below.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresTestContainerBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
        .withDatabaseName("docai_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Disable embedding/LLM API calls — tests inject mocks for these
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.ai.anthropic.api-key", () -> "");
        // Disable Redis
        registry.add("spring.data.redis.host", () -> "");
    }
}
