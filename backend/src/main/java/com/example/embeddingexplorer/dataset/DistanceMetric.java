package com.example.embeddingexplorer.dataset;

import java.util.Arrays;
import java.util.Optional;

/**
 * The pgvector distance metrics, with the SQL that Spring AI {@code PgVectorStore} uses for
 * each one, so that the tool computes distances and similarities exactly like the chatbot.
 */
public enum DistanceMetric {

    /** Cosine distance {@code <=>}, that is 1 − cosine similarity. Spring AI {@code COSINE_DISTANCE}. */
    COSINE("<=>", "vector_cosine_ops", "COSINE_DISTANCE"),

    /** Euclidean distance {@code <->}. Spring AI {@code EUCLIDEAN_DISTANCE}. */
    L2("<->", "vector_l2_ops", "EUCLIDEAN_DISTANCE"),

    /**
     * Negative inner product {@code <#>}. Spring AI {@code NEGATIVE_INNER_PRODUCT} reports
     * {@code 1 + (a <#> b)} as distance, which is 1 − dot product.
     */
    INNER_PRODUCT("<#>", "vector_ip_ops", "NEGATIVE_INNER_PRODUCT");

    private final String operator;
    private final String operatorClass;
    private final String springAiDistanceType;

    DistanceMetric(String operator, String operatorClass, String springAiDistanceType) {
        this.operator = operator;
        this.operatorClass = operatorClass;
        this.springAiDistanceType = springAiDistanceType;
    }

    public String operator() {
        return operator;
    }

    public String operatorClass() {
        return operatorClass;
    }

    public String springAiDistanceType() {
        return springAiDistanceType;
    }

    /** The distance as Spring AI defines it; the similarity threshold is applied to this value. */
    public String distanceSql(String column, String vector) {
        return this == INNER_PRODUCT
                ? "(1 + (" + column + " <#> " + vector + "))"
                : column + " " + operator + " " + vector;
    }

    /** The bare operator expression, the form an {@code ORDER BY … LIMIT} needs to use the vector index. */
    public String orderBySql(String column, String vector) {
        return column + " " + operator + " " + vector;
    }

    /**
     * The similarity search Spring AI {@code PgVectorStore} runs for this metric (without a
     * metadata filter); the third parameter is {@code 1 - similarityThreshold}.
     */
    public String springAiSearchSql(String table) {
        String distance = distanceSql("embedding", "?");
        return "SELECT *, " + distance + " AS distance FROM " + table + " WHERE " + distance
                + " < ? ORDER BY distance LIMIT ?";
    }

    /** Spring AI's score, 1 − distance. For cosine it is exactly the cosine similarity. */
    public double similarity(double distance) {
        return 1.0 - distance;
    }

    /** The metric served by an index operator class, for example {@code vector_cosine_ops}. */
    public static Optional<DistanceMetric> fromOperatorClass(String operatorClass) {
        return Arrays.stream(values())
                .filter(metric -> metric.operatorClass.equals(operatorClass))
                .findFirst();
    }
}
