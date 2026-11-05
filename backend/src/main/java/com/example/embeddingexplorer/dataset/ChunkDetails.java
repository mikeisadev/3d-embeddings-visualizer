package com.example.embeddingexplorer.dataset;

import tools.jackson.databind.JsonNode;

/**
 * Response of {@code GET /api/v1/chunks/{id}}: one chunk exactly as stored.
 *
 * @param id         chunk id
 * @param file       derived file
 * @param page       derived page, or {@code null}
 * @param chunkIndex derived chunk index, or {@code null}
 * @param content    full text (render it as plain text: it comes from arbitrary PDFs)
 * @param metadata   full metadata ({@code {}} when NULL or not an object)
 * @param embedding  every value of the vector, or {@code null} when the row has none
 * @param stats      statistics of the vector, or {@code null} when the row has none
 */
public record ChunkDetails(
        String id,
        String file,
        Integer page,
        Integer chunkIndex,
        String content,
        JsonNode metadata,
        float[] embedding,
        VectorStats stats) {}
