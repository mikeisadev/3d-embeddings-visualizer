package com.example.embeddingexplorer.dataset;

import java.util.Arrays;

/**
 * An immutable matrix of embeddings, one row per chunk, stored as a single row-major
 * {@code float[]}: 5,000 × 1,536 floats take about 30 MB, without per-row object overhead.
 */
public final class VectorMatrix {

    private final int rows;
    private final int dimensions;
    private final float[] data;

    private VectorMatrix(int rows, int dimensions, float[] data) {
        if (rows < 0 || dimensions < 0 || (long) rows * dimensions != data.length) {
            throw new IllegalArgumentException(
                    "Matrix of %d x %d does not match %d values".formatted(rows, dimensions, data.length));
        }
        this.rows = rows;
        this.dimensions = dimensions;
        this.data = data;
    }

    /** Builds a matrix from rows of equal length; the rows are copied. */
    public static VectorMatrix of(float[]... rows) {
        int dimensions = rows.length == 0 ? 0 : rows[0].length;
        float[] data = new float[rows.length * dimensions];
        for (int row = 0; row < rows.length; row++) {
            if (rows[row].length != dimensions) {
                throw new IllegalArgumentException("Row %d has %d values, expected %d"
                        .formatted(row, rows[row].length, dimensions));
            }
            System.arraycopy(rows[row], 0, data, row * dimensions, dimensions);
        }
        return new VectorMatrix(rows.length, dimensions, data);
    }

    public static VectorMatrix empty() {
        return new VectorMatrix(0, 0, new float[0]);
    }

    public int rows() {
        return rows;
    }

    public int dimensions() {
        return dimensions;
    }

    public float get(int row, int column) {
        return data[offset(row) + column];
    }

    /** A copy of one row. */
    public float[] row(int row) {
        int start = offset(row);
        return Arrays.copyOfRange(data, start, start + dimensions);
    }

    /** Copies one row into {@code target}, widening to double for numerically stable math. */
    public void copyRowInto(int row, double[] target) {
        int start = offset(row);
        for (int column = 0; column < dimensions; column++) {
            target[column] = data[start + column];
        }
    }

    /** Size of the values in bytes. */
    public long byteSize() {
        return (long) data.length * Float.BYTES;
    }

    private int offset(int row) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("Row " + row + " outside 0.." + (rows - 1));
        }
        return row * dimensions;
    }
}
