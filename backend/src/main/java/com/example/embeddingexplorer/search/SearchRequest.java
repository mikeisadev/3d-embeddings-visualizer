package com.example.embeddingexplorer.search;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/search}. Missing values get the defaults of the L18
 * {@code RagService} ({@code topK = 5}, {@code similarityThreshold = 0.5}) and
 * {@code normalize = true}.
 *
 * @param question            the question to embed, 1–2000 characters
 * @param topK                how many chunks to retrieve, 1–20
 * @param similarityThreshold minimum similarity to enter the context, 0–1
 * @param normalize           the normalize flag of the 3D view, to project the question like the points
 */
public record SearchRequest(
        @NotBlank @Size(min = 1, max = 2000) String question,
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold,
        Boolean normalize) {

    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    public SearchRequest {
        topK = topK == null ? DEFAULT_TOP_K : topK;
        similarityThreshold = similarityThreshold == null ? DEFAULT_SIMILARITY_THRESHOLD : similarityThreshold;
        normalize = normalize == null ? Boolean.TRUE : normalize;
    }
}
