package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.dataset.DistanceMetric;
import com.example.embeddingexplorer.dataset.VectorMatrix;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.stream.IntStream;

/**
 * Exact nearest neighbors computed in memory on the full vectors, with the same ordering as
 * pgvector: cosine distance for {@code <=>}, euclidean distance for {@code <->} and the
 * negative dot product for {@code <#>}. Undefined cosine distances (zero vectors, NaN in
 * PostgreSQL) sort last, as PostgreSQL sorts NaN after every number; ties go to the lower row.
 */
final class ExactNeighbors {

    private ExactNeighbors() {}

    /**
     * The {@code k} nearest rows of each query row, closest first, the query itself excluded.
     * Queries run in parallel; the result does not depend on the number of threads.
     */
    static int[][] of(VectorMatrix vectors, DistanceMetric metric, int[] queries, int k) {
        int neighbors = Math.max(0, Math.min(k, vectors.rows() - 1));
        double[] norms = metric == DistanceMetric.COSINE ? norms(vectors) : new double[0];
        return IntStream.range(0, queries.length)
                .parallel()
                .mapToObj(query -> nearest(vectors, metric, norms, queries[query], neighbors))
                .toArray(int[][]::new);
    }

    private static int[] nearest(VectorMatrix vectors, DistanceMetric metric, double[] norms, int query, int k) {
        if (k == 0) {
            return new int[0];
        }
        // Max-heap of the k best candidates so far: the worst one is on top, ready to be replaced.
        Comparator<Candidate> closestFirst = Comparator.comparingDouble(Candidate::key).thenComparingInt(Candidate::row);
        PriorityQueue<Candidate> best = new PriorityQueue<>(k + 1, closestFirst.reversed());
        for (int row = 0; row < vectors.rows(); row++) {
            if (row == query) {
                continue;
            }
            Candidate candidate = new Candidate(row, rankingKey(vectors, metric, norms, query, row));
            if (best.size() < k) {
                best.add(candidate);
            } else if (closestFirst.compare(candidate, best.peek()) < 0) {
                best.poll();
                best.add(candidate);
            }
        }
        return best.stream().sorted(closestFirst).mapToInt(Candidate::row).toArray();
    }

    /** A value that orders rows like the pgvector operator of the metric (smaller is closer). */
    private static double rankingKey(VectorMatrix vectors, DistanceMetric metric, double[] norms, int a, int b) {
        return switch (metric) {
            case COSINE -> {
                double lengths = norms[a] * norms[b];
                yield lengths == 0 ? Double.POSITIVE_INFINITY : 1.0 - vectors.dot(a, b) / lengths;
            }
            case L2 -> vectors.squaredDistance(a, b);
            case INNER_PRODUCT -> -vectors.dot(a, b);
        };
    }

    private static double[] norms(VectorMatrix vectors) {
        double[] norms = new double[vectors.rows()];
        for (int row = 0; row < norms.length; row++) {
            norms[row] = Math.sqrt(vectors.dot(row, row));
        }
        return norms;
    }

    private record Candidate(int row, double key) {}
}
