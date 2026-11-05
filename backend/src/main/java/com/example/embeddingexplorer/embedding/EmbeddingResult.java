package com.example.embeddingexplorer.embedding;

/**
 * An embedding returned by the provider.
 *
 * @param vector  the vector
 * @param model   the model name reported by the provider
 * @param latencyMillis how long the provider took to answer
 */
public record EmbeddingResult(float[] vector, String model, long latencyMillis) {}
