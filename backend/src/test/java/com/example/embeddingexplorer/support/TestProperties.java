package com.example.embeddingexplorer.support;

import com.example.embeddingexplorer.config.DistanceMetricSetting;
import com.example.embeddingexplorer.config.ExplorerProperties;
import java.time.Duration;

/** {@link ExplorerProperties} for service-level tests, with the production defaults. */
public final class TestProperties {

    private TestProperties() {}

    public static ExplorerProperties forTable(String table) {
        return forTable(table, DistanceMetricSetting.AUTO, 5000);
    }

    public static ExplorerProperties forTable(String table, DistanceMetricSetting metric, int maxPoints) {
        return new ExplorerProperties(
                database(table),
                metric,
                maxPoints,
                256,
                new ExplorerProperties.Embedding(false, "https://api.openai.com/v1", "text-embedding-3-small", ""),
                10);
    }

    public static ExplorerProperties withEmbedding(String table, ExplorerProperties.Embedding embedding) {
        return new ExplorerProperties(database(table), DistanceMetricSetting.AUTO, 5000, 256, embedding, 10);
    }

    private static ExplorerProperties.Database database(String table) {
        var container = PgvectorDatabase.container();
        return new ExplorerProperties.Database(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                "public",
                table,
                Duration.ofSeconds(15),
                Duration.ofSeconds(5));
    }
}
