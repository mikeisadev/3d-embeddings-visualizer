package com.example.embeddingexplorer.dataset;

import java.util.List;

/**
 * Response of {@code GET /api/v1/chunks?contains=…}: the chunks whose full text contains the
 * given words (case-insensitive). The 3D view highlights them.
 *
 * @param contains  the searched text
 * @param ids       matching chunk ids
 * @param truncated whether more chunks match than {@code VIZ_MAX_POINTS}
 */
public record ChunkMatches(String contains, List<String> ids, boolean truncated) {

    public ChunkMatches {
        ids = List.copyOf(ids);
    }
}
