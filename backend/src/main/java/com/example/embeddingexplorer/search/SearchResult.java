package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.ResolvedMetric;
import java.util.List;

/**
 * Response of {@code POST /api/v1/search}: what Spring AI would retrieve for the question, and
 * what the similarity threshold cuts.
 *
 * @param datasetVersion      fingerprint of the data searched
 * @param model               embedding model reported by the provider
 * @param embeddingLatencyMs  how long the provider took
 * @param dimensions          dimension of the question vector (equal to the dataset)
 * @param metric              the distance metric of the dataset
 * @param topK                chunks requested
 * @param similarityThreshold minimum similarity
 * @param distanceThreshold   {@code 1 - similarityThreshold}, the parameter Spring AI binds
 * @param normalized          whether the question point uses the normalized projection
 * @param queryPoint          the question projected with the same PCA as the points
 * @param results             the top-K chunks, closest first, with the threshold outcome
 * @param springAiSql         the SQL Spring AI {@code PgVectorStore} runs for this search
 */
public record SearchResult(
        String datasetVersion,
        String model,
        long embeddingLatencyMs,
        int dimensions,
        ResolvedMetric metric,
        int topK,
        double similarityThreshold,
        double distanceThreshold,
        boolean normalized,
        QueryPoint queryPoint,
        List<Match> results,
        String springAiSql) {

    public SearchResult {
        results = List.copyOf(results);
    }

    /**
     * The question in the 3D view; exact, because PCA is a linear projection.
     *
     * @param x coordinate on PC1
     * @param y coordinate on PC2
     * @param z coordinate on PC3
     */
    public record QueryPoint(float x, float y, float z) {}

    /**
     * One retrieved chunk.
     *
     * @param rank            1 for the closest
     * @param id              chunk id
     * @param distance        distance as Spring AI computes it
     * @param similarity      1 − distance
     * @param passesThreshold whether Spring AI would keep it ({@code distance < 1 - threshold})
     * @param file            derived file
     * @param page            derived page
     * @param chunkIndex      derived chunk index
     * @param preview         first characters of the text
     */
    public record Match(
            int rank,
            String id,
            Double distance,
            Double similarity,
            boolean passesThreshold,
            String file,
            Integer page,
            Integer chunkIndex,
            String preview) {}
}
