package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.dataset.ChunkRepository;
import com.example.embeddingexplorer.dataset.ChunkService;
import com.example.embeddingexplorer.dataset.MetricResolver;
import com.example.embeddingexplorer.dataset.ResolvedMetric;
import com.example.embeddingexplorer.dataset.VectorTableGuard;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Compares two chunks with every pgvector metric, in SQL, on the full vectors. */
@Service
public class CompareService {

    private final JdbcClient jdbc;
    private final TableRef table;
    private final ChunkRepository chunks;
    private final MetricResolver metrics;
    private final VectorTableGuard guard;

    public CompareService(
            JdbcClient jdbc, TableRef table, ChunkRepository chunks, MetricResolver metrics, VectorTableGuard guard) {
        this.jdbc = jdbc;
        this.table = table;
        this.chunks = chunks;
        this.metrics = metrics;
        this.guard = guard;
    }

    public Comparison compare(String a, String b) {
        guard.requireReadableVectorTable();
        ResolvedMetric metric = metrics.current();
        String sql = """
                SELECT a.embedding <=> b.embedding AS cosine_distance,
                       a.embedding <-> b.embedding AS euclidean_distance,
                       (a.embedding <#> b.embedding) * -1 AS dot_product
                FROM %1$s AS a CROSS JOIN %1$s AS b
                WHERE a.id::text = ? AND b.id::text = ?
                  AND a.embedding IS NOT NULL AND b.embedding IS NOT NULL""".formatted(table.sql());
        return jdbc.sql(sql)
                .params(a, b)
                .query((rs, rowNum) -> {
                    Double cosineDistance = finiteOrNull(rs, "cosine_distance");
                    return new Comparison(
                            a,
                            b,
                            cosineDistance == null ? null : 1.0 - cosineDistance,
                            cosineDistance,
                            finiteOrNull(rs, "euclidean_distance"),
                            finiteOrNull(rs, "dot_product"),
                            metric,
                            sql);
                })
                .optional()
                .orElseThrow(() -> missing(a, b));
    }

    private ExplorerException missing(String a, String b) {
        List<String> existing = chunks.existingIds(List.of(a, b));
        String missing = !existing.contains(a) ? a : !existing.contains(b) ? b : null;
        if (missing != null) {
            return ChunkService.notFound(missing);
        }
        return new ExplorerException(
                ErrorCode.CHUNK_NOT_FOUND,
                "Chunk " + a + " or " + b + " has no embedding.",
                Map.of("a", a, "b", b));
    }

    private static Double finiteOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() || !Double.isFinite(value) ? null : value;
    }
}
