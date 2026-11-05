package com.example.embeddingexplorer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the read-only connection pool from {@link ExplorerProperties}.
 *
 * <p>The pool is defined explicitly, instead of through {@code spring.datasource.*}, so that
 * every setting lives in the single validated properties record and the safety choices are
 * visible in one place:
 * <ul>
 *   <li>the session is read-only even for auto-commit statements ({@code readOnlyMode=always});</li>
 *   <li>every connection gets a statement timeout;</li>
 *   <li>the application starts even when the database is down
 *       ({@code initializationFailTimeout = -1}) and recovers by itself when it comes back.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class DatabaseConfig {

    private static final int MAX_POOL_SIZE = 5;
    private static final String APPLICATION_NAME = "embedding-explorer";

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(ExplorerProperties properties) {
        ExplorerProperties.Database db = properties.db();
        HikariConfig config = new HikariConfig();
        config.setPoolName("viz-db");
        config.setJdbcUrl(db.url());
        config.setUsername(db.user());
        config.setPassword(db.password());
        config.setReadOnly(true);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(db.connectionTimeout().toMillis());
        config.setInitializationFailTimeout(-1);
        // The value is a validated Duration, so building the statement from it is safe.
        config.setConnectionInitSql("SET statement_timeout = " + db.statementTimeout().toMillis());
        config.addDataSourceProperty("readOnlyMode", "always");
        config.addDataSourceProperty("connectTimeout", Math.max(1, db.connectionTimeout().toSeconds()));
        config.addDataSourceProperty("ApplicationName", APPLICATION_NAME);
        return new HikariDataSource(config);
    }

    @Bean
    TableRef vectorTable(ExplorerProperties properties) {
        return properties.db().tableRef();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
