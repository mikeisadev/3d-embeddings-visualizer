package com.example.embeddingexplorer.projection;

import com.example.embeddingexplorer.dataset.VectorMatrix;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Principal component analysis (PCA): the three directions along which the vectors differ the
 * most, the coordinates of every vector on them, and the share of variance they keep.
 *
 * <p>Method: block power iteration (orthogonal iteration) on the covariance matrix, with a
 * Rayleigh–Ritz step at each iteration.
 * <ul>
 *   <li>The 1536 × 1536 covariance matrix is never built: each iteration multiplies the
 *       centered data by a block of {@value #BLOCK_SIZE} vectors, about 2 × n × d × 8
 *       operations.</li>
 *   <li>Iterating 8 vectors to obtain 3 converges quickly even when two components have
 *       almost the same variance, which is common with text embeddings.</li>
 *   <li>It is deterministic: fixed seed, fixed operation order (rows are processed in
 *       fixed-size blocks summed in order, whatever the number of threads), and the sign of
 *       each component is fixed so that its largest coordinate is positive.</li>
 *   <li>The reported variance of each axis is the exact variance of the data along that axis
 *       (a Rayleigh quotient), so the numbers shown describe the projection on screen.</li>
 * </ul>
 */
public final class Pca {

    public static final int COMPONENTS = 3;

    static final int BLOCK_SIZE = 8;
    // Real embeddings converge in fewer than 20 iterations; only structureless data reaches the cap,
    // and then any three directions keep about the same (tiny) variance.
    static final int MAX_ITERATIONS = 100;
    static final double RESIDUAL_TOLERANCE = 1e-6;

    private static final long SEED = 17L;
    private static final int ROWS_PER_TASK = 256;
    private static final double RELATIVE_ZERO_VARIANCE = 1e-10;
    private static final double DEGENERATE_NORM = 1e-10;

    private Pca() {}

    /**
     * Fits the model and projects every row.
     *
     * @param vectors   one row per chunk
     * @param normalize whether to L2-normalize the rows first (cosine geometry)
     * @throws ExplorerException {@code PROJECTION_NOT_POSSIBLE} with fewer than 3 vectors,
     *     fewer than 3 dimensions or no variance at all
     */
    public static PcaProjection fit(VectorMatrix vectors, boolean normalize) {
        int rows = vectors.rows();
        int dimensions = vectors.dimensions();
        requirePossible(rows >= COMPONENTS, "PCA needs at least 3 vectors; the table has " + rows + ".");
        requirePossible(
                dimensions >= COMPONENTS, "PCA needs at least 3 dimensions; the vectors have " + dimensions + ".");
        CenteredData data = CenteredData.of(vectors, normalize);
        requirePossible(
                data.totalVariance > RELATIVE_ZERO_VARIANCE * Math.max(data.meanSquaredNorm, Double.MIN_NORMAL),
                "All vectors are identical: there is no variance to show.");

        int blockSize = Math.min(BLOCK_SIZE, dimensions);
        SplittableRandom random = new SplittableRandom(SEED);
        double[][] block = orthonormalize(randomBlock(blockSize, dimensions, random), random);
        RitzPairs ritz = null;
        int iteration = 0;
        boolean converged = false;
        while (iteration < MAX_ITERATIONS && !converged) {
            iteration++;
            double[][] product = multiplyByCovariance(data, block);
            ritz = RitzPairs.of(block, product);
            converged = ritz.maxResidual(COMPONENTS) <= RESIDUAL_TOLERANCE * ritz.values[0];
            block = orthonormalize(ritz.imageVectors(), random);
        }

        double[][] components = new double[COMPONENTS][];
        double[] eigenvalues = new double[COMPONENTS];
        for (int k = 0; k < COMPONENTS; k++) {
            components[k] = withLargestCoordinatePositive(ritz.vectors[k]);
            eigenvalues[k] = Math.max(ritz.values[k], 0.0);
        }
        PcaModel model = new PcaModel(
                data.mean, components, eigenvalues, data.totalVariance, normalize, iteration, converged);
        return new PcaProjection(model, data.project(components));
    }

    private static void requirePossible(boolean possible, String detail) {
        if (!possible) {
            throw new ExplorerException(ErrorCode.PROJECTION_NOT_POSSIBLE, detail);
        }
    }

    /** {@code C · V} for the covariance {@code C = (1/n) Σ wᵢ wᵢᵀ}, computed row block by row block. */
    private static double[][] multiplyByCovariance(CenteredData data, double[][] block) {
        int tasks = Math.ceilDiv(data.rows, ROWS_PER_TASK);
        List<double[][]> partials = IntStream.range(0, tasks)
                .parallel()
                .mapToObj(task -> partialProduct(data, block, task * ROWS_PER_TASK,
                        Math.min(data.rows, (task + 1) * ROWS_PER_TASK)))
                .toList();
        double[][] product = new double[block.length][data.dimensions];
        for (double[][] partial : partials) {
            for (int c = 0; c < block.length; c++) {
                for (int j = 0; j < data.dimensions; j++) {
                    product[c][j] += partial[c][j];
                }
            }
        }
        double inverseRows = 1.0 / data.rows;
        for (double[] column : product) {
            for (int j = 0; j < column.length; j++) {
                column[j] *= inverseRows;
            }
        }
        return product;
    }

    private static double[][] partialProduct(CenteredData data, double[][] block, int fromRow, int toRow) {
        int d = data.dimensions;
        double[][] partial = new double[block.length][d];
        double[] projections = new double[block.length];
        for (int row = fromRow; row < toRow; row++) {
            int offset = row * d;
            for (int c = 0; c < block.length; c++) {
                projections[c] = dot(data.values, offset, block[c], d);
            }
            for (int c = 0; c < block.length; c++) {
                double weight = projections[c];
                double[] target = partial[c];
                for (int j = 0; j < d; j++) {
                    target[j] += weight * data.values[offset + j];
                }
            }
        }
        return partial;
    }

    /** Dot product with four independent accumulators, so the CPU can pipeline the additions. */
    static double dot(float[] values, int offset, double[] vector, int length) {
        double s0 = 0;
        double s1 = 0;
        double s2 = 0;
        double s3 = 0;
        int j = 0;
        for (; j + 3 < length; j += 4) {
            s0 += values[offset + j] * vector[j];
            s1 += values[offset + j + 1] * vector[j + 1];
            s2 += values[offset + j + 2] * vector[j + 2];
            s3 += values[offset + j + 3] * vector[j + 3];
        }
        for (; j < length; j++) {
            s0 += values[offset + j] * vector[j];
        }
        return (s0 + s1) + (s2 + s3);
    }

    private static double[][] randomBlock(int size, int dimensions, SplittableRandom random) {
        double[][] block = new double[size][];
        for (int c = 0; c < size; c++) {
            block[c] = randomVector(dimensions, random);
        }
        return block;
    }

    private static double[] randomVector(int dimensions, SplittableRandom random) {
        double[] vector = new double[dimensions];
        for (int j = 0; j < dimensions; j++) {
            vector[j] = random.nextDouble(-1.0, 1.0);
        }
        return vector;
    }

    /**
     * Modified Gram–Schmidt, applied twice for stability. A vector that collapses (the data
     * has fewer directions than the block) is replaced with a fresh random one.
     */
    static double[][] orthonormalize(double[][] vectors, SplittableRandom random) {
        double[][] basis = new double[vectors.length][];
        for (int c = 0; c < vectors.length; c++) {
            double[] candidate = vectors[c].clone();
            double originalNorm = norm(candidate);
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < c; k++) {
                    subtractProjection(candidate, basis[k]);
                }
            }
            double norm = norm(candidate);
            while (norm <= DEGENERATE_NORM * Math.max(originalNorm, 1.0)) {
                candidate = randomVector(candidate.length, random);
                originalNorm = norm(candidate);
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < c; k++) {
                        subtractProjection(candidate, basis[k]);
                    }
                }
                norm = norm(candidate);
            }
            for (int j = 0; j < candidate.length; j++) {
                candidate[j] /= norm;
            }
            basis[c] = candidate;
        }
        return basis;
    }

    private static void subtractProjection(double[] vector, double[] unit) {
        double projection = dot(vector, unit);
        for (int j = 0; j < vector.length; j++) {
            vector[j] -= projection * unit[j];
        }
    }

    private static double[] withLargestCoordinatePositive(double[] component) {
        int largest = 0;
        for (int j = 1; j < component.length; j++) {
            if (Math.abs(component[j]) > Math.abs(component[largest])) {
                largest = j;
            }
        }
        double[] fixed = component.clone();
        if (fixed[largest] < 0) {
            for (int j = 0; j < fixed.length; j++) {
                fixed[j] = -fixed[j];
            }
        }
        return fixed;
    }

    static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += a[j] * b[j];
        }
        return sum;
    }

    private static double norm(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }

    /**
     * Rayleigh–Ritz step: the best approximations of the eigenpairs inside the span of the
     * current block, and their images under the covariance (used for the next iteration).
     */
    private static final class RitzPairs {

        private final double[] values;
        private final double[][] vectors;
        private final double[][] images;

        private RitzPairs(double[] values, double[][] vectors, double[][] images) {
            this.values = values;
            this.vectors = vectors;
            this.images = images;
        }

        /** {@code block} is orthonormal and {@code product = C · block}. */
        static RitzPairs of(double[][] block, double[][] product) {
            int size = block.length;
            double[][] reduced = new double[size][size];
            for (int r = 0; r < size; r++) {
                for (int c = r; c < size; c++) {
                    double value = 0.5 * (dot(block[r], product[c]) + dot(block[c], product[r]));
                    reduced[r][c] = value;
                    reduced[c][r] = value;
                }
            }
            SymmetricEigen eigen = SymmetricEigen.of(reduced);
            return new RitzPairs(eigen.values, combine(eigen.vectors, block), combine(eigen.vectors, product));
        }

        /** Largest residual {@code ‖C·z − θ·z‖} among the first {@code count} pairs. */
        double maxResidual(int count) {
            double worst = 0;
            for (int k = 0; k < count; k++) {
                double sum = 0;
                for (int j = 0; j < vectors[k].length; j++) {
                    double difference = images[k][j] - values[k] * vectors[k][j];
                    sum += difference * difference;
                }
                worst = Math.max(worst, Math.sqrt(sum));
            }
            return worst;
        }

        double[][] imageVectors() {
            return images;
        }

        private static double[][] combine(double[][] coefficients, double[][] basis) {
            int dimensions = basis[0].length;
            double[][] result = new double[coefficients.length][dimensions];
            for (int k = 0; k < coefficients.length; k++) {
                for (int c = 0; c < basis.length; c++) {
                    double weight = coefficients[k][c];
                    for (int j = 0; j < dimensions; j++) {
                        result[k][j] += weight * basis[c][j];
                    }
                }
            }
            return result;
        }
    }
}
