package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.TableRef;
import java.util.List;

/**
 * Response of {@code GET /api/v1/dataset}: what is in the vector table and how it is read.
 *
 * <p>Files and metadata statistics describe the loaded chunks; when {@code sampled} is true
 * they describe the sample, as the 3D view does.
 *
 * @param datasetVersion    fingerprint of the table content
 * @param schema            schema of the table
 * @param table             table name
 * @param rowCount          all rows in the table
 * @param rowsWithEmbedding rows with a non-NULL embedding
 * @param loadedRows        chunks loaded in the snapshot
 * @param sampled           whether the snapshot is a sample
 * @param maxPoints         the configured limit ({@code VIZ_MAX_POINTS})
 * @param dimensions        vector dimension, or {@code null} for an empty table
 * @param distanceMetric    metric in use and its origin
 * @param index             index on {@code embedding}, or {@code null}
 * @param files             chunks per file
 * @param metadataKeys      metadata key statistics
 * @param warnings          dirty-data warnings
 */
public record DatasetSummary(
        String datasetVersion,
        String schema,
        String table,
        long rowCount,
        long rowsWithEmbedding,
        int loadedRows,
        boolean sampled,
        int maxPoints,
        Integer dimensions,
        ResolvedMetric distanceMetric,
        VectorIndex index,
        List<DatasetSnapshot.FileCount> files,
        List<MetadataKeyStats> metadataKeys,
        List<DatasetSnapshot.Warning> warnings) {

    public static DatasetSummary of(DatasetSnapshot snapshot, TableRef table, int maxPoints) {
        return new DatasetSummary(
                snapshot.version(),
                table.schema().value(),
                table.table().value(),
                snapshot.totalRows(),
                snapshot.rowsWithEmbedding(),
                snapshot.chunks().size(),
                snapshot.sampled(),
                maxPoints,
                snapshot.dimensions(),
                snapshot.metric(),
                snapshot.index(),
                snapshot.files(),
                snapshot.metadataKeys(),
                snapshot.warnings());
    }
}
