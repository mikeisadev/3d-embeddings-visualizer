package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.ResolvedMetric;
import java.util.List;

/**
 * Response of {@code GET /api/v1/neighbors/sample}: the real nearest neighbors of a
 * deterministic sample of the loaded chunks, for the "Fedeltà della proiezione" score. The
 * frontend compares them with the nearest neighbors in the 3D view.
 *
 * @param datasetVersion the snapshot the neighbors were computed on
 * @param metric         the distance metric used
 * @param k              neighbors per sampled chunk (fewer than requested when the dataset is tiny)
 * @param loadedChunks   chunks in the snapshot, the candidates for every neighbor list
 * @param samples        one entry per sampled chunk, in reading order
 */
public record NeighborSample(
        String datasetVersion, ResolvedMetric metric, int k, int loadedChunks, List<Sample> samples) {

    public NeighborSample {
        samples = List.copyOf(samples);
    }

    /**
     * One sampled chunk.
     *
     * @param id        chunk id
     * @param neighbors ids of its real nearest neighbors, closest first
     */
    public record Sample(String id, List<String> neighbors) {

        public Sample {
            neighbors = List.copyOf(neighbors);
        }
    }
}
