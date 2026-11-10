package dev.cuccorese.brokerage;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** A throwaway Postgres loaded with the provided schema, like compose.yaml; Flyway migrates it on startup. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"))
                .withCopyFileToContainer(MountableFile.forHostPath("db/"), "/docker-entrypoint-initdb.d/");
    }
}
