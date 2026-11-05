package com.example.embeddingexplorer.dataset;

import java.util.Optional;

/**
 * An index on the {@code embedding} column, as found in the PostgreSQL catalog.
 *
 * @param name          index name, by default {@code spring_ai_vector_index}
 * @param type          access method, for example {@code hnsw} or {@code ivfflat}
 * @param operatorClass operator class, for example {@code vector_cosine_ops}
 */
public record VectorIndex(String name, String type, String operatorClass) {

    /** The distance metric this index accelerates, when the operator class is known. */
    public Optional<DistanceMetric> metric() {
        return DistanceMetric.fromOperatorClass(operatorClass);
    }
}
