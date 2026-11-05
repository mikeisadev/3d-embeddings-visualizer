package com.example.embeddingexplorer.dataset;

/**
 * Summary statistics of one embedding, computed on all its values.
 *
 * @param dimensions how many numbers the vector has
 * @param norm       L2 norm (length); OpenAI vectors are normalized to about 1
 * @param min        smallest value
 * @param max        largest value
 * @param mean       average value
 */
public record VectorStats(int dimensions, double norm, double min, double max, double mean) {

    public static VectorStats of(float[] vector) {
        if (vector.length == 0) {
            return new VectorStats(0, 0, 0, 0, 0);
        }
        double sum = 0;
        double squares = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (float value : vector) {
            sum += value;
            squares += (double) value * value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new VectorStats(vector.length, Math.sqrt(squares), min, max, sum / vector.length);
    }
}
