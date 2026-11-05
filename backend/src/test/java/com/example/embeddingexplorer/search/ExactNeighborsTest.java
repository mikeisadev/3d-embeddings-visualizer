package com.example.embeddingexplorer.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.embeddingexplorer.dataset.DistanceMetric;
import com.example.embeddingexplorer.dataset.VectorMatrix;
import com.example.embeddingexplorer.support.TestVectors;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExactNeighborsTest {

    /** Same direction as row 0 but much longer: close for cosine, far for L2. */
    private static final VectorMatrix VECTORS = VectorMatrix.of(
            new float[] {1f, 0f},
            new float[] {10f, 0.5f},
            new float[] {0.8f, 0.6f},
            new float[] {0f, 1f},
            new float[] {-1f, 0f});

    @Test
    void cosineIgnoresTheLengthOfTheVectors() {
        int[][] neighbors = ExactNeighbors.of(VECTORS, DistanceMetric.COSINE, new int[] {0}, 3);

        assertThat(neighbors[0]).containsExactly(1, 2, 3);
    }

    @Test
    void euclideanDistanceDependsOnTheLength() {
        int[][] neighbors = ExactNeighbors.of(VECTORS, DistanceMetric.L2, new int[] {0}, 4);

        assertThat(neighbors[0]).containsExactly(2, 3, 4, 1);
    }

    @Test
    void innerProductPrefersTheLargestDotProduct() {
        int[][] neighbors = ExactNeighbors.of(VECTORS, DistanceMetric.INNER_PRODUCT, new int[] {3}, 2);

        // Dot products with row 3: row 2 → 0.6, row 1 → 0.5, rows 0 and 4 → 0.
        assertThat(neighbors[0]).containsExactly(2, 1);
    }

    @Test
    void tiesGoToTheLowerRowAndZeroVectorsComeLast() {
        VectorMatrix vectors = VectorMatrix.of(
                new float[] {0f, 0f}, new float[] {1f, 0f}, new float[] {0f, 1f}, new float[] {0f, 2f});

        int[][] fromRow1 = ExactNeighbors.of(vectors, DistanceMetric.COSINE, new int[] {1}, 3);
        int[][] fromZero = ExactNeighbors.of(vectors, DistanceMetric.COSINE, new int[] {0}, 3);

        assertThat(fromRow1[0]).containsExactly(2, 3, 0);
        assertThat(fromZero[0]).containsExactly(1, 2, 3);
    }

    @Test
    void neverReturnsTheQueryAndCapsKAtTheOtherRows() {
        int[][] neighbors = ExactNeighbors.of(VECTORS, DistanceMetric.COSINE, new int[] {2, 4}, 50);

        assertThat(neighbors[0]).hasSize(4).doesNotContain(2);
        assertThat(neighbors[1]).hasSize(4).doesNotContain(4);
        assertThat(ExactNeighbors.of(VectorMatrix.of(new float[] {1f}), DistanceMetric.L2, new int[] {0}, 5)[0])
                .isEmpty();
    }

    @Test
    void matchesABruteForceSortOnRandomData() {
        float[][] rows = IntStream.range(0, 120)
                .mapToObj(seed -> TestVectors.random(24, seed))
                .toArray(float[][]::new);
        VectorMatrix vectors = VectorMatrix.of(rows);
        int[] queries = {0, 17, 64, 119};

        int[][] neighbors = ExactNeighbors.of(vectors, DistanceMetric.COSINE, queries, 10);

        for (int q = 0; q < queries.length; q++) {
            int query = queries[q];
            int[] expected = IntStream.range(0, rows.length)
                    .filter(row -> row != query)
                    .boxed()
                    .sorted(Comparator.comparingDouble(row -> -TestVectors.cosineSimilarity(rows[query], rows[row])))
                    .limit(10)
                    .mapToInt(Integer::intValue)
                    .toArray();
            assertThat(neighbors[q]).as("query %d", query).containsExactly(expected);
        }
    }

    @Test
    void samplesRowsEvenlyInReadingOrder() {
        assertThat(NeighborSampleService.evenlySpaced(10, 5)).containsExactly(0, 2, 4, 6, 8);
        assertThat(NeighborSampleService.evenlySpaced(3, 200)).containsExactly(0, 1, 2);
        assertThat(NeighborSampleService.evenlySpaced(0, 200)).isEmpty();
        int[] large = NeighborSampleService.evenlySpaced(5_000, 200);
        assertThat(large).hasSize(200).startsWith(0).endsWith(4_975);
        assertThat(Arrays.stream(large).distinct().count()).isEqualTo(200);
    }
}
