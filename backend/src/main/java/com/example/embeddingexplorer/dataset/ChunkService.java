package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Single chunks: full details and full-text matches. */
@Service
public class ChunkService {

    private final ChunkRepository chunks;
    private final VectorTableGuard guard;
    private final ExplorerProperties properties;

    public ChunkService(ChunkRepository chunks, VectorTableGuard guard, ExplorerProperties properties) {
        this.chunks = chunks;
        this.guard = guard;
        this.properties = properties;
    }

    public ChunkDetails details(String id) {
        guard.requireReadableVectorTable();
        return chunks.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Chunks containing {@code text}; at most {@code VIZ_MAX_POINTS} ids, like the points. */
    public ChunkMatches containing(String text) {
        guard.requireReadableVectorTable();
        return chunks.idsContaining(text.strip(), properties.maxPoints());
    }

    /** The error for an id that is not in the table (for example deleted by a re-indexing). */
    public static ExplorerException notFound(String id) {
        return new ExplorerException(
                ErrorCode.CHUNK_NOT_FOUND, "Chunk " + id + " does not exist (or has no embedding).", Map.of("id", id));
    }
}
