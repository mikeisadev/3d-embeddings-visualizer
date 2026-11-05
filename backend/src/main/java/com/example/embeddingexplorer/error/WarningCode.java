package com.example.embeddingexplorer.error;

/**
 * Stable codes for non-blocking problems: diagnostics checks with status {@code WARN} and
 * dataset warnings. Like {@link ErrorCode}, the frontend translates them into Italian.
 */
public enum WarningCode {
    TABLE_EMPTY,
    NULL_EMBEDDINGS_SKIPPED,
    DIMENSIONS_MIXED,
    INDEX_MISSING,
    METRIC_DIFFERS_FROM_INDEX,
    FILE_KEY_MISSING,
    METADATA_NOT_OBJECT,
    CONTENT_NULL,
    EMBEDDING_KEY_MISSING
}
