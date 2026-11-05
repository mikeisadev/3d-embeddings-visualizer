package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.dataset.ChunkFields;
import com.example.embeddingexplorer.dataset.DistanceMetric;
import com.example.embeddingexplorer.dataset.TextPreview;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The similarity queries shared by the neighbors and the RAG simulation: "the k rows closest
 * to this vector", written like Spring AI {@code PgVectorStore} writes them. The vector is
 * always a bound parameter cast to {@code vector}.
 */
@Component
class SimilarChunks {

    private final JdbcClient jdbc;
    private final TableRef table;
    private final JsonMapper jsonMapper;

    SimilarChunks(JdbcClient jdbc, TableRef table, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.table = table;
        this.jsonMapper = jsonMapper;
    }

    /** SQL of the top-k query; with {@code excludeId} the chunk itself is left out. */
    String topKSql(DistanceMetric metric, boolean excludeId) {
        String vector = "CAST(? AS vector)";
        return """
                SELECT id::text AS id, content::text AS content, metadata::text AS metadata,
                       %s AS distance
                FROM %s
                WHERE embedding IS NOT NULL%s
                ORDER BY %s
                LIMIT ?""".formatted(
                        metric.distanceSql("embedding", vector),
                        table.sql(),
                        excludeId ? " AND id::text <> ?" : "",
                        metric.orderBySql("embedding", vector));
    }

    /** Runs {@link #topKSql}; rows come back closest first. */
    List<Row> topK(DistanceMetric metric, String vectorLiteral, String excludeId, int k) {
        JdbcClient.StatementSpec statement = jdbc.sql(topKSql(metric, excludeId != null)).param(vectorLiteral);
        if (excludeId != null) {
            statement = statement.param(excludeId);
        }
        return statement.param(vectorLiteral).param(k).query((rs, rowNum) -> row(rs)).list();
    }

    private Row row(ResultSet rs) throws SQLException {
        JsonNode metadata = parseMetadata(rs.getString("metadata"));
        String content = rs.getString("content");
        double value = rs.getDouble("distance");
        // A zero vector has no cosine distance: PostgreSQL returns NaN (or NULL), JSON has no NaN.
        Double distance = rs.wasNull() || Double.isNaN(value) ? null : value;
        return new Row(
                rs.getString("id"),
                distance,
                ChunkFields.file(metadata),
                ChunkFields.page(metadata),
                ChunkFields.chunkIndex(metadata),
                TextPreview.preview(content == null ? "" : content));
    }

    private JsonNode parseMetadata(String json) {
        try {
            JsonNode node = json == null ? null : jsonMapper.readTree(json);
            return node != null && node.isObject() ? node : JsonNodeFactory.instance.objectNode();
        } catch (JacksonException invalidJson) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    /**
     * A row of a similarity query.
     *
     * @param id         chunk id
     * @param distance   distance as Spring AI defines it, or {@code null} when undefined
     * @param file       derived file
     * @param page       derived page
     * @param chunkIndex derived chunk index
     * @param preview    first characters of the text
     */
    record Row(String id, Double distance, String file, Integer page, Integer chunkIndex, String preview) {}
}
