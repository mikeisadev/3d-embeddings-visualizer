package com.example.embeddingexplorer.projection;

import java.util.Arrays;
import java.util.List;

/**
 * The fitted PCA transform: mean, three principal components and the variance they keep.
 *
 * <p>The question of the RAG simulation (and, optionally, file centroids) is projected with
 * {@link #project(float[])}, exactly the transform applied to the points on screen.
 */
public final class PcaModel {

    private final double[] mean;
    private final double[][] components;
    private final double[] eigenvalues;
    private final double totalVariance;
    private final boolean normalized;
    private final int iterations;
    private final boolean converged;

    PcaModel(
            double[] mean,
            double[][] components,
            double[] eigenvalues,
            double totalVariance,
            boolean normalized,
            int iterations,
            boolean converged) {
        this.mean = mean.clone();
        this.components = Arrays.stream(components).map(double[]::clone).toArray(double[][]::new);
        this.eigenvalues = eigenvalues.clone();
        this.totalVariance = totalVariance;
        this.normalized = normalized;
        this.iterations = iterations;
        this.converged = converged;
    }

    /** Coordinates of a vector on the three components (same transform as the points). */
    public float[] project(float[] vector) {
        if (vector.length != mean.length) {
            throw new IllegalArgumentException(
                    "Vector has %d dimensions, the model %d".formatted(vector.length, mean.length));
        }
        double scale = 1.0;
        if (normalized) {
            double norm = 0;
            for (float value : vector) {
                norm += (double) value * value;
            }
            scale = norm > 0 ? 1.0 / Math.sqrt(norm) : 1.0;
        }
        float[] coordinates = new float[components.length];
        for (int k = 0; k < components.length; k++) {
            double sum = 0;
            for (int j = 0; j < mean.length; j++) {
                sum += (vector[j] * scale - mean[j]) * components[k][j];
            }
            coordinates[k] = (float) sum;
        }
        return coordinates;
    }

    /** Share of the total variance kept by each component, largest first. */
    public List<Double> explainedVarianceRatio() {
        return Arrays.stream(eigenvalues).map(value -> value / totalVariance).boxed().toList();
    }

    /** Share of the total variance kept by the three components together. */
    public double totalExplainedVariance() {
        return Arrays.stream(eigenvalues).sum() / totalVariance;
    }

    /** A copy of component {@code k} (a unit vector). */
    public double[] component(int k) {
        return components[k].clone();
    }

    public int dimensions() {
        return mean.length;
    }

    public boolean normalized() {
        return normalized;
    }

    public int iterations() {
        return iterations;
    }

    public boolean converged() {
        return converged;
    }
}
