package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.ResolvedMetric;

/**
 * Response of {@code GET /api/v1/compare}: how similar two chunks really are, computed by
 * PostgreSQL on the full vectors. Values are {@code null} when undefined (zero vectors).
 *
 * @param a                 first chunk id
 * @param b                 second chunk id
 * @param cosineSimilarity  1 − cosine distance
 * @param cosineDistance    {@code a <=> b}
 * @param euclideanDistance {@code a <-> b}
 * @param dotProduct        {@code -(a <#> b)}; equals the cosine similarity for unit vectors
 * @param metric            the metric of the dataset, to say which distance the chatbot uses
 * @param sql               the SQL that was executed, for display
 */
public record Comparison(
        String a,
        String b,
        Double cosineSimilarity,
        Double cosineDistance,
        Double euclideanDistance,
        Double dotProduct,
        ResolvedMetric metric,
        String sql) {}
