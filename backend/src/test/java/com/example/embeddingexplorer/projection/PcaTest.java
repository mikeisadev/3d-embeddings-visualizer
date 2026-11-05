package com.example.embeddingexplorer.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.embeddingexplorer.dataset.VectorMatrix;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.support.TestVectors;
import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PcaTest {

    private static final int[] AXES = {7, 2, 30};
    private static final double[] SIGMAS = {5.0, 3.0, 2.0};
    private static final double NOISE = 0.1;
    private static final int DIMENSIONS = 40;

    @Test
    void findsTheKnownAxesInOrder() {
        PcaModel model = Pca.fit(knownAxes(4000), false).model();

        for (int k = 0; k < 3; k++) {
            assertThat(model.component(k)[AXES[k]]).as("component %d", k).isGreaterThan(0.999);
        }
    }

    @Test
    void matchesAnExactEigendecompositionOfTheCovariance() {
        VectorMatrix data = clustered(300, 20);
        PcaModel model = Pca.fit(data, false).model();

        SymmetricEigen exact = SymmetricEigen.of(covariance(data));
        double totalVariance = 0;
        for (double value : exact.values) {
            totalVariance += value;
        }
        for (int k = 0; k < 3; k++) {
            assertThat(Math.abs(Pca.dot(model.component(k), exact.vectors[k])))
                    .as("component %d", k)
                    .isCloseTo(1.0, within(1e-8));
            // The PCA works on float copies of the data: ratios agree to float precision.
            assertThat(model.explainedVarianceRatio().get(k)).isCloseTo(exact.values[k] / totalVariance, within(1e-6));
        }
    }

    @Test
    void explainedVarianceMatchesTheGeneratedData() {
        List<Double> ratios = Pca.fit(knownAxes(4000), false).model().explainedVarianceRatio();

        double total = 25 + 9 + 4 + DIMENSIONS * NOISE * NOISE / 3;
        assertThat(ratios.get(0)).isCloseTo(25 / total, within(0.03));
        assertThat(ratios.get(1)).isCloseTo(9 / total, within(0.02));
        assertThat(ratios.get(2)).isCloseTo(4 / total, within(0.02));
    }

    @Test
    void componentsAreOrthonormal() {
        PcaModel model = Pca.fit(clustered(500, 64), true).model();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double dot = Pca.dot(model.component(i), model.component(j));
                assertThat(dot).isCloseTo(i == j ? 1.0 : 0.0, within(1e-9));
            }
        }
    }

    @Test
    void ratiosAreBetweenZeroAndOneAndDecreasing() {
        PcaModel model = Pca.fit(clustered(500, 64), true).model();
        List<Double> ratios = model.explainedVarianceRatio();

        assertThat(ratios).allSatisfy(ratio -> assertThat(ratio).isBetween(0.0, 1.0));
        assertThat(ratios.get(0)).isGreaterThanOrEqualTo(ratios.get(1));
        assertThat(ratios.get(1)).isGreaterThanOrEqualTo(ratios.get(2));
        assertThat(model.totalExplainedVariance()).isBetween(0.0, 1.0);
        assertThat(model.converged()).isTrue();
    }

    @Test
    void isDeterministic() {
        VectorMatrix data = clustered(700, 96);

        PcaProjection first = Pca.fit(data, true);
        PcaProjection second = Pca.fit(data, true);

        for (int k = 0; k < 3; k++) {
            assertThat(second.model().component(k)).containsExactly(first.model().component(k));
        }
        for (int row = 0; row < data.rows(); row++) {
            for (int axis = 0; axis < 3; axis++) {
                assertThat(second.coordinate(row, axis)).isEqualTo(first.coordinate(row, axis));
            }
        }
    }

    @Test
    void largestCoordinateOfEachComponentIsPositive() {
        PcaModel model = Pca.fit(clustered(300, 32), true).model();

        for (int k = 0; k < 3; k++) {
            double[] component = model.component(k);
            double largest = 0;
            for (double value : component) {
                largest = Math.abs(value) > Math.abs(largest) ? value : largest;
            }
            assertThat(largest).isPositive();
        }
    }

    @Test
    void projectingATrainingVectorGivesItsCoordinates() {
        VectorMatrix data = clustered(200, 48);
        PcaProjection projection = Pca.fit(data, true);

        for (int row = 0; row < data.rows(); row += 37) {
            float[] coordinates = projection.model().project(data.row(row));
            for (int axis = 0; axis < 3; axis++) {
                assertThat(coordinates[axis]).isCloseTo(projection.coordinate(row, axis), within(1e-5f));
            }
        }
    }

    @Test
    void normalizationIgnoresTheLengthOfTheVectors() {
        VectorMatrix data = clustered(100, 24);
        PcaModel model = Pca.fit(data, true).model();
        float[] vector = data.row(5);
        float[] longer = vector.clone();
        for (int j = 0; j < longer.length; j++) {
            longer[j] *= 7.5f;
        }

        assertThat(model.project(longer)).containsExactly(model.project(vector), within(1e-5f));
    }

    @Test
    void handlesFewerVectorsThanTheBlockSize() {
        VectorMatrix data = VectorMatrix.of(
                TestVectors.random(50, 1), TestVectors.random(50, 2), TestVectors.random(50, 3));

        PcaModel model = Pca.fit(data, true).model();

        assertThat(model.explainedVarianceRatio().get(2)).isCloseTo(0.0, within(1e-6));
        assertThat(model.totalExplainedVariance()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void refusesFewerThanThreeVectors() {
        VectorMatrix data = VectorMatrix.of(TestVectors.random(8, 1), TestVectors.random(8, 2));

        assertProjectionNotPossible(data, "at least 3 vectors");
    }

    @Test
    void refusesIdenticalVectors() {
        float[] vector = TestVectors.random(8, 1);

        assertProjectionNotPossible(VectorMatrix.of(vector, vector, vector, vector), "identical");
    }

    @Test
    void refusesFewerThanThreeDimensions() {
        assertProjectionNotPossible(
                VectorMatrix.of(new float[] {1, 0}, new float[] {0, 1}, new float[] {1, 1}), "3 dimensions");
    }

    @Test
    void projects5000Vectors1536DimensionsInUnderTwoSeconds() {
        VectorMatrix data = realisticEmbeddings(5000, 1536);
        Pca.fit(realisticEmbeddings(500, 1536), true);

        long start = System.nanoTime();
        PcaProjection projection = Pca.fit(data, true);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        System.out.printf(
                "PCA 5000 x 1536: %d ms, %d iterations, explained %.3f%n",
                elapsed.toMillis(),
                projection.model().iterations(),
                projection.model().totalExplainedVariance());
        assertThat(projection.model().converged()).isTrue();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    private static double[][] covariance(VectorMatrix data) {
        int d = data.dimensions();
        double[] mean = new double[d];
        double[] row = new double[d];
        for (int i = 0; i < data.rows(); i++) {
            data.copyRowInto(i, row);
            for (int j = 0; j < d; j++) {
                mean[j] += row[j] / data.rows();
            }
        }
        double[][] covariance = new double[d][d];
        for (int i = 0; i < data.rows(); i++) {
            data.copyRowInto(i, row);
            for (int a = 0; a < d; a++) {
                for (int b = 0; b < d; b++) {
                    covariance[a][b] += (row[a] - mean[a]) * (row[b] - mean[b]) / data.rows();
                }
            }
        }
        return covariance;
    }

    private static void assertProjectionNotPossible(VectorMatrix data, String detail) {
        assertThatThrownBy(() -> Pca.fit(data, true))
                .isInstanceOfSatisfying(ExplorerException.class, error -> assertThat(error.code())
                        .isEqualTo(ErrorCode.PROJECTION_NOT_POSSIBLE))
                .hasMessageContaining(detail);
    }

    /** Gaussian data stretched along three known axes, plus small isotropic noise. */
    private static VectorMatrix knownAxes(int rows) {
        SplittableRandom random = new SplittableRandom(3);
        float[][] data = new float[rows][DIMENSIONS];
        for (float[] row : data) {
            for (int j = 0; j < DIMENSIONS; j++) {
                row[j] = (float) (NOISE * random.nextDouble(-1, 1));
            }
            for (int k = 0; k < 3; k++) {
                row[AXES[k]] += (float) (SIGMAS[k] * gaussian(random));
            }
        }
        return VectorMatrix.of(data);
    }

    private static VectorMatrix clustered(int rows, int dimensions) {
        float[][] data = new float[rows][];
        for (int i = 0; i < rows; i++) {
            data[i] = TestVectors.clustered(dimensions, (i % 4) * 5, i, 0.3);
        }
        return VectorMatrix.of(data);
    }

    /**
     * Unit vectors with a few topic clusters and a slowly decaying spectrum, like text
     * embeddings: the first components explain a minority of the variance.
     */
    private static VectorMatrix realisticEmbeddings(int rows, int dimensions) {
        SplittableRandom random = new SplittableRandom(11);
        float[][] centers = new float[6][];
        for (int c = 0; c < centers.length; c++) {
            centers[c] = TestVectors.random(dimensions, 1000 + c);
        }
        float[][] data = new float[rows][dimensions];
        for (int i = 0; i < rows; i++) {
            float[] center = centers[i % centers.length];
            for (int j = 0; j < dimensions; j++) {
                double decay = 1.0 / Math.sqrt(1.0 + j / 8.0);
                data[i][j] = (float) (0.6 * center[j] + 0.08 * decay * gaussian(random));
            }
            data[i] = TestVectors.normalize(data[i]);
        }
        return VectorMatrix.of(data);
    }

    private static double gaussian(SplittableRandom random) {
        return random.nextGaussian();
    }
}
