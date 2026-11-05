package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.ChunkRepository;
import com.example.embeddingexplorer.dataset.ChunkService;
import com.example.embeddingexplorer.dataset.MetricResolver;
import com.example.embeddingexplorer.dataset.ResolvedMetric;
import com.example.embeddingexplorer.dataset.VectorTableGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The real nearest neighbors of a chunk, computed by PostgreSQL on the full vectors with the
 * dataset metric.
 *
 * <p>The query is the classic {@code ORDER BY embedding <=> CAST(? AS vector) LIMIT ?}, but
 * index scans are disabled for its transaction: an HNSW index is approximate and returns at
 * most {@code hnsw.ef_search} (40) rows, while this tool shows the ground truth the 3D view is
 * compared with. Scanning a classroom table (a few thousand rows) takes milliseconds. The RAG
 * simulation, on the contrary, uses the index exactly like the chatbot.
 */
@Service
public class NeighborService {

    public static final int MAX_K = 50;

    private final ChunkRepository chunks;
    private final SimilarChunks similarChunks;
    private final MetricResolver metrics;
    private final VectorTableGuard guard;
    private final JdbcClient jdbc;
    private final TransactionTemplate readOnlyTransaction;

    public NeighborService(
            ChunkRepository chunks,
            SimilarChunks similarChunks,
            MetricResolver metrics,
            VectorTableGuard guard,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager) {
        this.chunks = chunks;
        this.similarChunks = similarChunks;
        this.metrics = metrics;
        this.guard = guard;
        this.jdbc = jdbc;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    public Neighbors neighbors(String id, int k) {
        guard.requireReadableVectorTable();
        ResolvedMetric metric = metrics.current();
        String vector = chunks.findVectorLiteral(id).orElseThrow(() -> ChunkService.notFound(id));
        List<SimilarChunks.Row> rows = Objects.requireNonNull(readOnlyTransaction.execute(status -> {
            jdbc.sql("SELECT set_config('enable_indexscan', 'off', true)").query(String.class).single();
            return similarChunks.topK(metric.metric(), vector, id, k);
        }));
        List<Neighbors.Neighbor> neighbors = new ArrayList<>(rows.size());
        for (SimilarChunks.Row row : rows) {
            neighbors.add(new Neighbors.Neighbor(
                    neighbors.size() + 1,
                    row.id(),
                    row.distance(),
                    row.distance() == null ? null : metric.metric().similarity(row.distance()),
                    row.file(),
                    row.page(),
                    row.chunkIndex(),
                    row.preview()));
        }
        return new Neighbors(id, k, metric, true, similarChunks.topKSql(metric.metric(), true), neighbors);
    }
}
