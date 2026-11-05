package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.DistanceMetricSetting;
import java.util.List;
import java.util.Objects;

/**
 * The distance metric in use and where it comes from, so the UI can say whether it was
 * detected from the index or configured.
 *
 * @param metric the metric used for neighbors, comparisons and the RAG simulation
 * @param source how the metric was chosen
 */
public record ResolvedMetric(DistanceMetric metric, Source source) {

    /** How the metric was chosen. */
    public enum Source {
        /** From the operator class of the index on {@code embedding}. */
        DETECTED,
        /** From {@code VIZ_DISTANCE_METRIC}. */
        CONFIGURED,
        /** {@code auto} found no usable index: cosine, the Spring AI default. */
        DEFAULT
    }

    public ResolvedMetric {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(source, "source");
    }

    /**
     * Resolves the metric: an explicit setting wins; {@code auto} uses the first index with a
     * known operator class and falls back to cosine.
     */
    public static ResolvedMetric resolve(DistanceMetricSetting setting, List<VectorIndex> indexes) {
        return switch (setting) {
            case COSINE -> new ResolvedMetric(DistanceMetric.COSINE, Source.CONFIGURED);
            case L2 -> new ResolvedMetric(DistanceMetric.L2, Source.CONFIGURED);
            case INNER_PRODUCT -> new ResolvedMetric(DistanceMetric.INNER_PRODUCT, Source.CONFIGURED);
            case AUTO -> indexes.stream()
                    .flatMap(index -> index.metric().stream())
                    .findFirst()
                    .map(metric -> new ResolvedMetric(metric, Source.DETECTED))
                    .orElseGet(() -> new ResolvedMetric(DistanceMetric.COSINE, Source.DEFAULT));
        };
    }
}
