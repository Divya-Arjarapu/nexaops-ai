package com.nexaops.backend.config;

import com.nexaops.backend.NexaOpsBackendApplication;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(
        classes = NexaOpsBackendApplication.class,
        properties = {
                "POSTGRES_HOST=127.0.0.1",
                "POSTGRES_PORT=1",
                "POSTGRES_DB=nexaops_test",
                "POSTGRES_USER=test-user",
                "POSTGRES_PASSWORD=test-password",
                "spring.flyway.enabled=false",
                "spring.datasource.hikari.initialization-fail-timeout=-1",
                "spring.datasource.hikari.minimum-idle=0"
        }
)
class DataSourceConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void datasourceUsesPostgresEnvironmentSettingsWithoutOpeningConnection() {
        HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, dataSource);

        assertEquals("jdbc:postgresql://127.0.0.1:1/nexaops_test", hikariDataSource.getJdbcUrl());
        assertEquals("test-user", hikariDataSource.getUsername());
        assertEquals("test-password", hikariDataSource.getPassword());
    }
}
