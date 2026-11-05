package com.example.embeddingexplorer.projection;

import com.example.embeddingexplorer.dataset.VectorMatrix;

/**
 * The working copy of the vectors for {@link Pca}: optionally L2-normalized, then centered on
 * the mean. Stored as one row-major {@code float[]} (half the memory of doubles); every sum is
 * accumulated in double precision.
 */
final class CenteredData {

    final int rows;
    final int dimensions;
    final float[] values;
    final double[] mean;
    final double totalVariance;
    final double meanSquaredNorm;

    private CenteredData(
            int rows, int dimensions, float[] values, double[] mean, double totalVariance, double meanSquaredNorm) {
        this.rows = rows;
        this.dimensions = dimensions;
        this.values = values;
        this.mean = mean;
        this.totalVariance = totalVariance;
        this.meanSquaredNorm = meanSquaredNorm;
    }

    static CenteredData of(VectorMatrix vectors, boolean normalize) {
        int rows = vectors.rows();
        int dimensions = vectors.dimensions();
        float[] values = new float[rows * dimensions];
        double[] mean = new double[dimensions];
        double[] row = new double[dimensions];
        double squaredNorms = 0;
        for (int i = 0; i < rows; i++) {
            vectors.copyRowInto(i, row);
            double squaredNorm = Pca.dot(row, row);
            double scale = normalize && squaredNorm > 0 ? 1.0 / Math.sqrt(squaredNorm) : 1.0;
            squaredNorms += squaredNorm * scale * scale;
            int offset = i * dimensions;
            for (int j = 0; j < dimensions; j++) {
                double value = row[j] * scale;
                values[offset + j] = (float) value;
                mean[j] += value;
            }
        }
        for (int j = 0; j < dimensions; j++) {
            mean[j] /= rows;
        }
        double variance = 0;
        for (int i = 0; i < rows; i++) {
            int offset = i * dimensions;
            for (int j = 0; j < dimensions; j++) {
                float centered = (float) (values[offset + j] - mean[j]);
                values[offset + j] = centered;
                variance += (double) centered * centered;
            }
        }
        return new CenteredData(rows, dimensions, values, mean, variance / rows, squaredNorms / rows);
    }

    /** Coordinates of every row on the components, row-major ({@code rows × components}). */
    float[] project(double[][] components) {
        float[] coordinates = new float[rows * components.length];
        for (int i = 0; i < rows; i++) {
            int offset = i * dimensions;
            for (int k = 0; k < components.length; k++) {
                coordinates[i * components.length + k] = (float) Pca.dot(values, offset, components[k], dimensions);
            }
        }
        return coordinates;
    }
}
