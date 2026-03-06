package com.mappo.controlplane.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URISyntaxException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        String jdbcUrl = trim(environment.getProperty("MAPPO_JDBC_DATABASE_URL"));
        if (jdbcUrl.isEmpty()) {
            jdbcUrl = convertSqlAlchemyUrl(trim(environment.getProperty("MAPPO_DATABASE_URL")));
        }
        if (jdbcUrl.isEmpty()) {
            jdbcUrl = trim(environment.getProperty("spring.datasource.url"));
        }

        String username = trim(environment.getProperty("MAPPO_DB_USER"));
        if (username.isEmpty()) {
            username = trim(environment.getProperty("spring.datasource.username"));
        }

        String password = environment.getProperty("MAPPO_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            password = environment.getProperty("spring.datasource.password", "");
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(8);
        return dataSource;
    }

    private String convertSqlAlchemyUrl(String sqlAlchemyUrl) {
        if (sqlAlchemyUrl == null || sqlAlchemyUrl.isBlank()) {
            return "";
        }

        String normalized = sqlAlchemyUrl
            .replaceFirst("^postgresql\\+psycopg://", "postgresql://")
            .replaceFirst("^postgresql://", "postgresql://");

        try {
            URI uri = new URI(normalized);
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
            jdbc.append(uri.getHost());
            if (uri.getPort() > 0) {
                jdbc.append(":").append(uri.getPort());
            }
            jdbc.append(uri.getPath());
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                jdbc.append("?").append(uri.getQuery());
            }
            return jdbc.toString();
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
