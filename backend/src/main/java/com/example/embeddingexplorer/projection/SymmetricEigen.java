package com.example.embeddingexplorer.projection;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Eigenvalues and eigenvectors of a small symmetric matrix (a few rows), with the cyclic
 * Jacobi method: simple, numerically robust and deterministic. Used for the Rayleigh–Ritz
 * step of {@link Pca}, where the matrix is only 8 × 8.
 */
final class SymmetricEigen {

    private static final int MAX_SWEEPS = 100;

    /** Eigenvalues, largest first. */
    final double[] values;

    /** {@code vectors[k]} is the unit eigenvector of {@code values[k]}. */
    final double[][] vectors;

    private SymmetricEigen(double[] values, double[][] vectors) {
        this.values = values;
        this.vectors = vectors;
    }

    static SymmetricEigen of(double[][] symmetric) {
        int size = symmetric.length;
        double[][] a = new double[size][];
        for (int i = 0; i < size; i++) {
            a[i] = Arrays.copyOf(symmetric[i], size);
        }
        double[][] v = new double[size][size];
        for (int i = 0; i < size; i++) {
            v[i][i] = 1.0;
        }
        for (int sweep = 0; sweep < MAX_SWEEPS && offDiagonalNorm(a) > 1e-15 * frobeniusNorm(a); sweep++) {
            for (int p = 0; p < size - 1; p++) {
                for (int q = p + 1; q < size; q++) {
                    rotate(a, v, p, q);
                }
            }
        }
        Integer[] order = IntStream.range(0, size).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> a[i][i]).reversed());
        double[] values = new double[size];
        double[][] vectors = new double[size][size];
        for (int k = 0; k < size; k++) {
            int column = order[k];
            values[k] = a[column][column];
            for (int row = 0; row < size; row++) {
                vectors[k][row] = v[row][column];
            }
        }
        return new SymmetricEigen(values, vectors);
    }

    /** One Jacobi rotation that zeroes {@code a[p][q]}; {@code v} accumulates the rotations. */
    private static void rotate(double[][] a, double[][] v, int p, int q) {
        double apq = a[p][q];
        if (apq == 0.0) {
            return;
        }
        double theta = (a[q][q] - a[p][p]) / (2.0 * apq);
        double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
        if (theta == 0.0) {
            t = 1.0;
        }
        double c = 1.0 / Math.sqrt(t * t + 1.0);
        double s = t * c;
        int size = a.length;
        for (int k = 0; k < size; k++) {
            double akp = a[k][p];
            double akq = a[k][q];
            a[k][p] = c * akp - s * akq;
            a[k][q] = s * akp + c * akq;
        }
        for (int k = 0; k < size; k++) {
            double apk = a[p][k];
            double aqk = a[q][k];
            a[p][k] = c * apk - s * aqk;
            a[q][k] = s * apk + c * aqk;
        }
        for (int k = 0; k < size; k++) {
            double vkp = v[k][p];
            double vkq = v[k][q];
            v[k][p] = c * vkp - s * vkq;
            v[k][q] = s * vkp + c * vkq;
        }
    }

    private static double offDiagonalNorm(double[][] a) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a.length; j++) {
                if (i != j) {
                    sum += a[i][j] * a[i][j];
                }
            }
        }
        return Math.sqrt(sum);
    }

    private static double frobeniusNorm(double[][] a) {
        double sum = 0;
        for (double[] row : a) {
            for (double value : row) {
                sum += value * value;
            }
        }
        return Math.max(Math.sqrt(sum), Double.MIN_NORMAL);
    }
}
