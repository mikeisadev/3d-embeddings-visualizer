package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.error.WarningCode;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, consistent copy of the vector table (or of a deterministic sample of it),
 * loaded once and shared by all requests until the table changes.
 *
 * <p>{@code chunks.get(i)} and {@code vectors.row(i)} describe the same chunk; rows are in
 * reading order (file, page, chunk index, id).
 *
 * @param version           fingerprint of the table content ({@code datasetVersion} in the API)
 * @param totalRows         rows in the table, including those without an embedding
 * @param rowsWithEmbedding rows with a non-NULL embedding
 * @param sampled           whether only a sample of {@code rowsWithEmbedding} was loaded
 * @param dimensions        vector dimension, or {@code null} when the table has no vectors
 * @param metric            the distance metric in use
 * @param index             the index on {@code embedding}, or {@code null} when there is none
 * @param chunks            one entry per loaded chunk
 * @param vectors           one row per loaded chunk
 * @param metadataKeys      statistics of the metadata keys of the loaded chunks
 * @param files             chunk count per file, largest first
 * @param warnings          dirty-data warnings found while loading
 */
public record DatasetSnapshot(
        String version,
        long totalRows,
        long rowsWithEmbedding,
        boolean sampled,
        Integer dimensions,
        ResolvedMetric metric,
        VectorIndex index,
        List<ChunkPoint> chunks,
        VectorMatrix vectors,
        List<MetadataKeyStats> metadataKeys,
        List<FileCount> files,
        List<Warning> warnings) {

    public DatasetSnapshot {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(vectors, "vectors");
        chunks = List.copyOf(chunks);
        metadataKeys = List.copyOf(metadataKeys);
        files = List.copyOf(files);
        warnings = List.copyOf(warnings);
        if (chunks.size() != vectors.rows()) {
            throw new IllegalArgumentException("Chunks and vectors differ in size");
        }
    }

    /**
     * Chunks of one file.
     *
     * @param file   derived file label
     * @param chunks how many loaded chunks belong to it
     */
    public record FileCount(String file, int chunks) {}

    /**
     * A dirty-data problem that did not prevent loading.
     *
     * @param code  stable warning code
     * @param count affected rows
     */
    public record Warning(WarningCode code, long count) {}
}
