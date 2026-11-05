package com.example.embeddingexplorer.projection;

/**
 * A fitted {@link PcaModel} and the 3D coordinates of the rows it was fitted on.
 */
public final class PcaProjection {

    private final PcaModel model;
    private final float[] coordinates;

    PcaProjection(PcaModel model, float[] coordinates) {
        this.model = model;
        this.coordinates = coordinates;
    }

    public PcaModel model() {
        return model;
    }

    public int size() {
        return coordinates.length / Pca.COMPONENTS;
    }

    /** Coordinate {@code axis} (0, 1, 2) of row {@code row}. */
    public float coordinate(int row, int axis) {
        return coordinates[row * Pca.COMPONENTS + axis];
    }
}
