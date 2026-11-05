package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.ResolvedMetric;
import java.util.List;

/**
 * Response of {@code GET /api/v1/chunks/{id}/neighbors}: the real nearest neighbors, computed
 * by PostgreSQL on the full vectors.
 *
 * @param id        the chunk whose neighbors were searched
 * @param k         how many neighbors were requested
 * @param metric    the distance metric used
 * @param exact     always {@code true}: the search scans every vector instead of the approximate index
 * @param sql       the SQL template that was executed, for display
 * @param neighbors the neighbors, closest first
 */
public record Neighbors(String id, int k, ResolvedMetric metric, boolean exact, String sql, List<Neighbor> neighbors) {

    public Neighbors {
        neighbors = List.copyOf(neighbors);
    }

    /**
     * One neighbor.
     *
     * @param rank       1 for the closest
     * @param id         chunk id
     * @param distance   distance as Spring AI computes it, or {@code null} when undefined (zero vector)
     * @param similarity 1 − distance (the cosine similarity for the cosine metric), or {@code null}
     * @param file       derived file
     * @param page       derived page, or {@code null}
     * @param chunkIndex derived chunk index, or {@code null}
     * @param preview    first characters of the text
     */
    public record Neighbor(
            int rank,
            String id,
            Double distance,
            Double similarity,
            String file,
            Integer page,
            Integer chunkIndex,
            String preview) {}
}
