package br.com.cronicas.credito;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The pgvector container every {@code @SpringBootTest} in this project needs.
 *
 * <p>It lives at the root of the test tree rather than inside {@code politica}
 * because the dependency is not on retrieval, it is on the context starting at
 * all: the vector store bean is wired whenever the application context loads, so
 * a classifier test with no interest in retrieval still fails without a database.
 * Before this moved, that test quietly required {@code docker compose up} to have
 * been run by hand, and said so nowhere.
 *
 * <p>The container type comes from {@code org.testcontainers.postgresql}, not the
 * old {@code org.testcontainers.containers}. Testcontainers 2.x moved each module
 * into its own package and left the old type behind as a deprecated alias, which
 * is also why it is no longer generic: {@code PostgreSQLContainer<?>} was the 1.x
 * self-type idiom.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer pgvectorContainer() {
        return new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
    }
}
