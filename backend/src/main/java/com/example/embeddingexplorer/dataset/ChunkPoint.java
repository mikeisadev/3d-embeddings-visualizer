package com.example.embeddingexplorer.dataset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the 3D view needs to know about one chunk, without its full text or vector.
 *
 * @param id            the row id, an opaque string (UUID in L17)
 * @param file          derived file label, see {@link ChunkFields#file}
 * @param page          derived page, or {@code null}
 * @param chunkIndex    derived chunk index, or {@code null}
 * @param preview       first characters of the content, on one line
 * @param contentLength length of the full content in characters
 * @param metadata      scalar metadata values, see {@link ChunkFields#scalarMetadata}
 */
public record ChunkPoint(
        String id,
        String file,
        Integer page,
        Integer chunkIndex,
        String preview,
        int contentLength,
        Map<String, Object> metadata) {

    public ChunkPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(preview, "preview");
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
