package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.ChunkPoint;
import com.example.embeddingexplorer.dataset.DatasetService;
import com.example.embeddingexplorer.dataset.DatasetSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The real nearest neighbors of a deterministic sample of chunks, used to measure how many
 * of them the 3D projection keeps close.
 *
 * <p>The neighbors are searched among the loaded chunks only, the same points the 3D view
 * draws: with a sampled table, a neighbor that is not on screen could never count as kept.
 * They are computed in memory on the full snapshot vectors with the dataset metric (one
 * exhaustive scan per sampled chunk, a fraction of a second for 200 × 5,000 vectors), which
 * gives the same order as the exact database query of {@link NeighborService}.
 */
@Service
public class NeighborSampleService {

    public static final int MIN_SIZE = 10;
    public static final int MAX_SIZE = 1_000;

    private final DatasetService datasets;

    public NeighborSampleService(DatasetService datasets) {
        this.datasets = datasets;
    }

    public NeighborSample sample(int size, int k) {
        DatasetSnapshot snapshot = datasets.currentSnapshot();
        List<ChunkPoint> chunks = snapshot.chunks();
        int[] sampled = evenlySpaced(chunks.size(), size);
        int[][] neighbors = ExactNeighbors.of(snapshot.vectors(), snapshot.metric().metric(), sampled, k);
        List<NeighborSample.Sample> samples = new ArrayList<>(sampled.length);
        for (int i = 0; i < sampled.length; i++) {
            List<String> ids = Arrays.stream(neighbors[i]).mapToObj(row -> chunks.get(row).id()).toList();
            samples.add(new NeighborSample.Sample(chunks.get(sampled[i]).id(), ids));
        }
        int effectiveK = Math.max(0, Math.min(k, chunks.size() - 1));
        return new NeighborSample(snapshot.version(), snapshot.metric(), effectiveK, chunks.size(), samples);
    }

    /**
     * {@code size} row indexes spread evenly over {@code rows} rows (all of them when there are
     * fewer). The rows are in reading order, so every file is represented in proportion.
     */
    static int[] evenlySpaced(int rows, int size) {
        int count = Math.min(rows, size);
        int[] indexes = new int[count];
        for (int i = 0; i < count; i++) {
            indexes[i] = (int) ((long) i * rows / count);
        }
        return indexes;
    }
}
