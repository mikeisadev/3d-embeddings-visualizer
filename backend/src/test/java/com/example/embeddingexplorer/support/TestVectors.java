package com.example.embeddingexplorer.support;

import java.util.SplittableRandom;
import java.util.StringJoiner;

/** Deterministic synthetic embeddings for tests. */
public final class TestVectors {

    private TestVectors() {}

    /** A unit vector close to the axis {@code cluster}, with seeded noise. */
    public static float[] clustered(int dimensions, int cluster, long seed, double noise) {
        SplittableRandom random = new SplittableRandom(seed);
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vector[i] = (float) (noise * (random.nextDouble() * 2 - 1));
        }
        vector[cluster % dimensions] += 1.0f;
        return normalize(vector);
    }

    /** A unit vector with seeded random components. */
    public static float[] random(int dimensions, long seed) {
        return clustered(dimensions, 0, seed, 1.0);
    }

    public static float[] normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    /** The pgvector text form, for example {@code [0.1,0.2]}. */
    public static String literal(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
