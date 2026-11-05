package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.error.WarningCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads a {@link DatasetSnapshot} in one read-only {@code REPEATABLE READ} transaction, so
 * the fingerprint, the counts and the rows come from the same database snapshot even while
 * the chatbot is indexing a PDF.
 *
 * <p>At most {@code VIZ_MAX_POINTS} rows are loaded. Above that, the rows are chosen by
 * {@code md5(id)}: a deterministic sample that does not favour any file. The final order
 * (file, page, chunk index, id) is applied in Java with the same derivation rules used
 * everywhere else, see {@link ChunkFields}. The caller validates the table first with
 * {@link VectorTableGuard}.
 */
@Component
public class SnapshotLoader {

    private static final int FETCH_SIZE = 500;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final JdbcClient jdbc;
    private final TableInspector inspector;
    private final TableRef table;
    private final ExplorerProperties properties;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;

    public SnapshotLoader(
            JdbcClient jdbc,
            TableInspector inspector,
            TableRef table,
            ExplorerProperties properties,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.inspector = inspector;
        this.table = table;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public DatasetSnapshot load() {
        return Objects.requireNonNull(transaction.execute(status -> loadConsistently()));
    }

    private DatasetSnapshot loadConsistently() {
        String version = inspector.fingerprint(table);
        TableInspector.RowCounts counts = inspector.rowCounts(table);
        Integer dimensions = uniqueDimension(inspector.dimensionGroups(table));
        List<VectorIndex> indexes = inspector.embeddingIndexes(table);
        ResolvedMetric metric = ResolvedMetric.resolve(properties.distanceMetric(), indexes);

        SnapshotRowCollector.Result rows = dimensions == null
                ? new SnapshotRowCollector(jsonMapper, 0).result()
                : loadRows(dimensions, (int) Math.min(counts.withEmbedding(), properties.maxPoints()));

        List<DatasetSnapshot.Warning> warnings = new ArrayList<>();
        if (counts.withoutEmbedding() > 0) {
            warnings.add(new DatasetSnapshot.Warning(WarningCode.NULL_EMBEDDINGS_SKIPPED, counts.withoutEmbedding()));
        }
        warnings.addAll(rows.warnings());

        return new DatasetSnapshot(
                version,
                counts.total(),
                counts.withEmbedding(),
                rows.chunks().size() < counts.withEmbedding(),
                dimensions,
                metric,
                indexForMetric(indexes),
                rows.chunks(),
                rows.vectors(),
                rows.metadataKeys(),
                rows.files(),
                warnings);
    }

    private SnapshotRowCollector.Result loadRows(int dimensions, int expectedRows) {
        requireWithinMemoryBudget(expectedRows, dimensions);
        SnapshotRowCollector collector = new SnapshotRowCollector(jsonMapper, dimensions);
        jdbc.sql("""
                        WITH sample AS (
                            SELECT id
                            FROM %1$s
                            WHERE embedding IS NOT NULL
                            ORDER BY md5(id::text), id::text
                            LIMIT ?
                        )
                        SELECT t.id::text AS id,
                               t.content::text AS content,
                               t.metadata::text AS metadata,
                               t.embedding::real[] AS embedding
                        FROM %1$s AS t
                        JOIN sample ON sample.id = t.id
                        """.formatted(table.sql()))
                .param(properties.maxPoints())
                .withFetchSize(FETCH_SIZE)
                .query(collector);
        return collector.result();
    }

    private void requireWithinMemoryBudget(int rows, int dimensions) {
        long estimatedBytes = (long) rows * dimensions * Float.BYTES;
        long budgetMb = properties.snapshotMemoryBudgetMb();
        if (estimatedBytes > budgetMb * BYTES_PER_MB) {
            long estimatedMb = Math.ceilDiv(estimatedBytes, BYTES_PER_MB);
            throw new ExplorerException(
                    ErrorCode.SNAPSHOT_TOO_LARGE,
                    "Loading %d vectors of %d dimensions needs about %d MB, above the budget of %d MB."
                            .formatted(rows, dimensions, estimatedMb, budgetMb),
                    Map.of("estimatedMb", estimatedMb, "budgetMb", budgetMb));
        }
    }

    /** One vector size per table: mixing models would make every distance meaningless. */
    private static Integer uniqueDimension(List<TableInspector.DimensionGroup> groups) {
        if (groups.isEmpty()) {
            return null;
        }
        if (groups.size() > 1) {
            List<Integer> found = groups.stream().map(TableInspector.DimensionGroup::dimensions).toList();
            throw new ExplorerException(
                    ErrorCode.DIMENSION_MISMATCH,
                    "The table mixes vectors with different dimensions " + found + "; use one table per model.",
                    Map.of("dimensionsFound", found));
        }
        return groups.getFirst().dimensions();
    }

    /** The index that serves the metric, or the first index on the column, or {@code null}. */
    private static VectorIndex indexForMetric(List<VectorIndex> indexes) {
        return indexes.stream()
                .filter(index -> index.metric().isPresent())
                .findFirst()
                .orElse(indexes.isEmpty() ? null : indexes.getFirst());
    }
}
