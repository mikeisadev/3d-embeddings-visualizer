package com.example.embeddingexplorer.config;

/**
 * The configured distance metric ({@code VIZ_DISTANCE_METRIC}).
 *
 * <p>{@link #AUTO} detects the metric from the operator class of the index on the
 * {@code embedding} column, which is how Spring AI {@code PgVectorStore} records the
 * distance type it was configured with.
 */
public enum DistanceMetricSetting {
    AUTO,
    COSINE,
    L2,
    INNER_PRODUCT
}
