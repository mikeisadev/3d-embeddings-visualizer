package com.example.embeddingexplorer.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.embeddingexplorer.config.DistanceMetricSetting;
import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.dataset.TableInspector;
import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestProperties;
import com.example.embeddingexplorer.support.TestVectors;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The diagnostics checklist against real PostgreSQL + pgvector tables in different states. */
class DiagnosticsServiceIT {

    private static final int DIMENSIONS = 16;

    @BeforeAll
    static void createTables() {
        PgvectorDatabase.createVectorTable("diag_ok", DIMENSIONS);
        PgvectorDatabase.insert("diag_ok", SampleData.rows(DIMENSIONS, 6));
        PgvectorDatabase.createHnswIndex("diag_ok", "vector_cosine_ops");

        PgvectorDatabase.createVectorTable("diag_empty", DIMENSIONS);

        PgvectorDatabase.admin().execute("DROP TABLE IF EXISTS public.diag_wrong");
        PgvectorDatabase.admin().execute(
                "CREATE TABLE public.diag_wrong (id uuid PRIMARY KEY, text_content text, metadata text, embedding real[])");

        PgvectorDatabase.createVectorTable("diag_nofile", DIMENSIONS);
        PgvectorDatabase.insert("diag_nofile", List.of(new PgvectorDatabase.TestRow(
                SampleData.uuid(1), "testo", "{\"titolo\": \"x\"}", TestVectors.random(DIMENSIONS, 1))));

        PgvectorDatabase.createVectorTable("diag_l2", DIMENSIONS);
        PgvectorDatabase.insert("diag_l2", SampleData.rows(DIMENSIONS, 2));
        PgvectorDatabase.createHnswIndex("diag_l2", "vector_l2_ops");

        PgvectorDatabase.admin().execute("DROP TABLE IF EXISTS public.diag_mixed");
        PgvectorDatabase.admin().execute(
                "CREATE TABLE public.diag_mixed (id uuid PRIMARY KEY, content text, metadata json, embedding vector)");
        PgvectorDatabase.insert("diag_mixed", List.of(
                new PgvectorDatabase.TestRow(SampleData.uuid(1), "a", "{}", TestVectors.random(3, 1)),
                new PgvectorDatabase.TestRow(SampleData.uuid(2), "b", "{}", TestVectors.random(4, 2))));

        PgvectorDatabase.createVectorTable("diag_nulls", DIMENSIONS);
        PgvectorDatabase.insert("diag_nulls", List.of(
                new PgvectorDatabase.TestRow(SampleData.uuid(1), "a", "{}", TestVectors.random(DIMENSIONS, 1)),
                new PgvectorDatabase.TestRow(SampleData.uuid(2), "b", "{}", null)));
    }

    @Test
    void healthyTablePassesEveryCheck() {
        DiagnosticsReport report = diagnostics(TestProperties.forTable("diag_ok")).run();
        Map<String, DiagnosticCheck> checks = byId(report);

        assertThat(report.checks()).extracting(DiagnosticCheck::id).containsExactly(
                "db.connection", "db.pgvector", "table.exists", "table.columns", "table.rows",
                "embedding.dimensions", "embedding.norms", "table.index", "metadata.keys", "embedding.client");
        assertThat(report.checks().subList(0, 9)).allSatisfy(check -> assertThat(check.status())
                .as(check.id() + ": " + check.detail())
                .isEqualTo(CheckStatus.OK));
        assertThat(report.status()).isEqualTo(CheckStatus.OK);
        assertThat(checks.get("db.connection").facts()).containsKeys("serverVersion", "database", "url");
        assertThat(checks.get("db.pgvector").facts()).containsKey("version");
        assertThat(checks.get("embedding.dimensions").facts()).containsEntry("dimensions", DIMENSIONS);
        assertThat(checks.get("embedding.norms").facts()).containsEntry("unitLength", true);
        assertThat(checks.get("table.index").facts())
                .containsEntry("indexType", "hnsw")
                .containsEntry("operatorClass", "vector_cosine_ops")
                .containsEntry("metric", "COSINE")
                .containsEntry("metricSource", "DETECTED");
        assertThat(checks.get("metadata.keys").facts()).containsEntry("fileKey", "original_filename");
        assertThat(checks.get("embedding.client").status()).isEqualTo(CheckStatus.SKIPPED);
        assertThat(checks.get("embedding.client").code()).isEqualTo("EMBEDDING_DISABLED");
    }

    @Test
    void emptyTableWarnsAndSkipsTheChecksThatNeedRows() {
        Map<String, DiagnosticCheck> checks = byId(diagnostics(TestProperties.forTable("diag_empty")).run());

        assertThat(checks.get("table.rows").status()).isEqualTo(CheckStatus.WARN);
        assertThat(checks.get("table.rows").code()).isEqualTo("TABLE_EMPTY");
        assertThat(checks.get("embedding.dimensions").status()).isEqualTo(CheckStatus.SKIPPED);
        assertThat(checks.get("embedding.norms").status()).isEqualTo(CheckStatus.SKIPPED);
        assertThat(checks.get("metadata.keys").status()).isEqualTo(CheckStatus.SKIPPED);
        assertThat(checks.get("table.index").code()).isEqualTo("INDEX_MISSING");
    }

    @Test
    void missingTableFailsAndSkipsItsDependents() {
        DiagnosticsReport report = diagnostics(TestProperties.forTable("diag_does_not_exist")).run();
        Map<String, DiagnosticCheck> checks = byId(report);

        assertThat(report.status()).isEqualTo(CheckStatus.FAIL);
        assertThat(checks.get("db.connection").status()).isEqualTo(CheckStatus.OK);
        assertThat(checks.get("table.exists").status()).isEqualTo(CheckStatus.FAIL);
        assertThat(checks.get("table.exists").code()).isEqualTo("TABLE_NOT_FOUND");
        assertThat(List.of("table.columns", "table.rows", "embedding.dimensions", "embedding.norms", "table.index",
                        "metadata.keys"))
                .allSatisfy(id -> assertThat(checks.get(id).status()).isEqualTo(CheckStatus.SKIPPED));
    }

    @Test
    void wrongColumnsAreReported() {
        Map<String, DiagnosticCheck> checks = byId(diagnostics(TestProperties.forTable("diag_wrong")).run());

        DiagnosticCheck columns = checks.get("table.columns");
        assertThat(columns.status()).isEqualTo(CheckStatus.FAIL);
        assertThat(columns.code()).isEqualTo("TABLE_SCHEMA_INVALID");
        assertThat(columns.detail())
                .contains("missing column 'content'")
                .contains("column 'metadata' has type text")
                .contains("column 'embedding' has type real[]");
        assertThat(checks.get("table.rows").status()).isEqualTo(CheckStatus.SKIPPED);
    }

    @Test
    void metadataWithoutFileKeysWarns() {
        DiagnosticCheck metadata = byId(diagnostics(TestProperties.forTable("diag_nofile")).run()).get("metadata.keys");

        assertThat(metadata.status()).isEqualTo(CheckStatus.WARN);
        assertThat(metadata.code()).isEqualTo("FILE_KEY_MISSING");
        assertThat(metadata.facts()).containsEntry("keys", List.of("titolo"));
    }

    @Test
    void detectsTheEuclideanMetricFromTheIndex() {
        DiagnosticCheck index = byId(diagnostics(TestProperties.forTable("diag_l2")).run()).get("table.index");

        assertThat(index.status()).isEqualTo(CheckStatus.OK);
        assertThat(index.facts()).containsEntry("metric", "L2").containsEntry("metricSource", "DETECTED");
    }

    @Test
    void warnsWhenTheConfiguredMetricDoesNotMatchTheIndex() {
        ExplorerProperties properties = TestProperties.forTable("diag_l2", DistanceMetricSetting.COSINE, 5000);
        DiagnosticCheck index = byId(diagnostics(properties).run()).get("table.index");

        assertThat(index.status()).isEqualTo(CheckStatus.WARN);
        assertThat(index.code()).isEqualTo("METRIC_DIFFERS_FROM_INDEX");
    }

    @Test
    void warnsAboutMixedDimensions() {
        DiagnosticCheck dimensions =
                byId(diagnostics(TestProperties.forTable("diag_mixed")).run()).get("embedding.dimensions");

        assertThat(dimensions.status()).isEqualTo(CheckStatus.WARN);
        assertThat(dimensions.code()).isEqualTo("DIMENSIONS_MIXED");
        assertThat(dimensions.detail()).contains("[3, 4]");
    }

    @Test
    void warnsAboutRowsWithoutEmbedding() {
        DiagnosticCheck rows = byId(diagnostics(TestProperties.forTable("diag_nulls")).run()).get("table.rows");

        assertThat(rows.status()).isEqualTo(CheckStatus.WARN);
        assertThat(rows.code()).isEqualTo("NULL_EMBEDDINGS_SKIPPED");
        assertThat(rows.facts()).containsEntry("rowsWithoutEmbedding", 1L);
    }

    @Test
    void warnsWhenOpenAiHasNoKey() {
        ExplorerProperties properties = TestProperties.withEmbedding(
                "diag_ok",
                new ExplorerProperties.Embedding(true, "https://api.openai.com/v1", "text-embedding-3-small", " "));
        DiagnosticCheck client = byId(diagnostics(properties).run()).get("embedding.client");

        assertThat(client.status()).isEqualTo(CheckStatus.WARN);
        assertThat(client.code()).isEqualTo("EMBEDDING_KEY_MISSING");
        assertThat(client.facts()).containsEntry("apiKeyPresent", false).doesNotContainKey("apiKey");
    }

    @Test
    void reportsAMissingSelectPermission() {
        PgvectorDatabase.admin().execute("DROP ROLE IF EXISTS no_select_user");
        PgvectorDatabase.admin().execute("CREATE ROLE no_select_user LOGIN PASSWORD 'pw'");
        var container = PgvectorDatabase.container();
        DriverManagerDataSource restricted =
                new DriverManagerDataSource(container.getJdbcUrl(), "no_select_user", "pw");
        ExplorerProperties properties = TestProperties.forTable("diag_ok");
        DiagnosticsService service = new DiagnosticsService(
                new TableInspector(JdbcClient.create(restricted)),
                properties.db().tableRef(),
                properties,
                Clock.systemUTC());

        DiagnosticCheck exists = byId(service.run()).get("table.exists");

        assertThat(exists.status()).isEqualTo(CheckStatus.FAIL);
        assertThat(exists.code()).isEqualTo("DB_PERMISSION_DENIED");
    }

    private static DiagnosticsService diagnostics(ExplorerProperties properties) {
        var container = PgvectorDatabase.container();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return new DiagnosticsService(
                new TableInspector(JdbcClient.create(dataSource)),
                properties.db().tableRef(),
                properties,
                Clock.systemUTC());
    }

    private static Map<String, DiagnosticCheck> byId(DiagnosticsReport report) {
        return report.checks().stream().collect(Collectors.toMap(DiagnosticCheck::id, Function.identity()));
    }
}
