package com.example.embeddingexplorer.support;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One pgvector container shared by all integration tests (singleton container pattern):
 * starting PostgreSQL once keeps the suite fast. Each test class works on its own tables,
 * created here with an admin connection; the application itself only reads.
 */
public final class PgvectorDatabase {

    public static final String IMAGE = "pgvector/pgvector:pg17";

    private static final PostgreSQLContainer CONTAINER = startContainer();
    private static final JdbcTemplate ADMIN = new JdbcTemplate(adminDataSource());

    private PgvectorDatabase() {}

    public static PostgreSQLContainer container() {
        return CONTAINER;
    }

    public static JdbcTemplate admin() {
        return ADMIN;
    }

    /** Points the application to the shared database and to {@code table}. */
    public static void register(DynamicPropertyRegistry registry, String table) {
        registry.add("viz.db.url", CONTAINER::getJdbcUrl);
        registry.add("viz.db.user", CONTAINER::getUsername);
        registry.add("viz.db.password", CONTAINER::getPassword);
        registry.add("viz.db.table", () -> table);
    }

    /** Drops and creates a table with the exact structure Spring AI {@code PgVectorStore} creates. */
    public static void createVectorTable(String table, int dimensions) {
        ADMIN.execute("DROP TABLE IF EXISTS public." + table);
        ADMIN.execute("""
                CREATE TABLE public.%s (
                    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
                    content text,
                    metadata json,
                    embedding vector(%d)
                )""".formatted(table, dimensions));
    }

    public static void createHnswIndex(String table, String operatorClass) {
        ADMIN.execute("CREATE INDEX %s_idx ON public.%s USING hnsw (embedding %s)"
                .formatted(table, table, operatorClass));
    }

    public static void insert(String table, List<TestRow> rows) {
        ADMIN.batchUpdate(
                "INSERT INTO public." + table + " (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::json, ?::vector)",
                rows.stream()
                        .map(row -> new Object[] {
                            row.id(), row.content(), row.metadataJson(), row.vector() == null ? null : TestVectors.literal(row.vector())
                        })
                        .toList());
    }

    private static PostgreSQLContainer startContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer(IMAGE);
        container.start();
        return container;
    }

    private static DataSource adminDataSource() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        JdbcTemplate setup = new JdbcTemplate(dataSource);
        setup.execute("CREATE EXTENSION IF NOT EXISTS vector");
        setup.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        return dataSource;
    }

    /**
     * A row to insert.
     *
     * @param id           UUID text
     * @param content      chunk text, may be {@code null}
     * @param metadataJson metadata JSON text, may be {@code null}
     * @param vector       embedding, may be {@code null}
     */
    public record TestRow(String id, String content, String metadataJson, float[] vector) {}
}
