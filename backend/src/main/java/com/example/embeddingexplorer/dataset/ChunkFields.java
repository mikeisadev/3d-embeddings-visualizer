package com.example.embeddingexplorer.dataset;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Fields derived from the free-form metadata written by Spring AI. No key is guaranteed to
 * exist, so every lookup has a fallback:
 * <ul>
 *   <li><b>file</b>: {@code original_filename}, then {@code file_name}, then {@code source};
 *       otherwise {@value #UNKNOWN_FILE};</li>
 *   <li><b>page</b>: {@code page_number} (written by the PDF reader), then {@code start_page_number};</li>
 *   <li><b>chunk index</b>: {@code chunk_index} (written by recent text splitters).</li>
 * </ul>
 * Numbers may arrive as JSON numbers or as numeric strings; both are accepted.
 */
public final class ChunkFields {

    /** The file label of chunks without any file key (shown as is in the UI). */
    public static final String UNKNOWN_FILE = "Sconosciuto";

    /** Metadata keys that name the source file, by priority. */
    public static final List<String> FILE_KEYS = List.of("original_filename", "file_name", "source");

    static final List<String> PAGE_KEYS = List.of("page_number", "start_page_number");
    static final String CHUNK_INDEX_KEY = "chunk_index";

    /** Reading order inside the dataset: file, page, chunk index, then id. Missing values go last. */
    public static final Comparator<ChunkPoint> READING_ORDER = Comparator.comparing(ChunkPoint::file)
            .thenComparing(ChunkPoint::page, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ChunkPoint::chunkIndex, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ChunkPoint::id);

    private static final Pattern INTEGER_TEXT = Pattern.compile("^\\s*[+-]?\\d{1,9}\\s*$");
    private static final int METADATA_STRING_LIMIT = 200;

    private ChunkFields() {}

    public static String file(JsonNode metadata) {
        return FILE_KEYS.stream()
                .map(metadata::get)
                .filter(Objects::nonNull)
                .filter(value -> value.isValueNode() && !value.isNull())
                .map(value -> value.asString().strip())
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElse(UNKNOWN_FILE);
    }

    public static Integer page(JsonNode metadata) {
        return PAGE_KEYS.stream()
                .map(key -> integer(metadata.get(key)))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    public static Integer chunkIndex(JsonNode metadata) {
        return integer(metadata.get(CHUNK_INDEX_KEY)).orElse(null);
    }

    /**
     * The scalar members of the metadata (strings, numbers, booleans), with strings cut at
     * 200 characters: enough to color and filter the points without sending nested data.
     */
    public static Map<String, Object> scalarMetadata(JsonNode metadata) {
        Map<String, Object> scalars = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : metadata.properties()) {
            JsonNode value = entry.getValue();
            if (value.isString()) {
                scalars.put(entry.getKey(), TextPreview.truncate(value.stringValue(), METADATA_STRING_LIMIT));
            } else if (value.isNumber()) {
                scalars.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                scalars.put(entry.getKey(), value.booleanValue());
            }
        }
        return scalars;
    }

    static Optional<Integer> integer(JsonNode value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isNumber()) {
            return value.canConvertToExactIntegral() && value.canConvertToInt()
                    ? Optional.of(value.asInt())
                    : Optional.empty();
        }
        if (value.isString() && INTEGER_TEXT.matcher(value.stringValue()).matches()) {
            return Optional.of(Integer.parseInt(value.stringValue().strip()));
        }
        return Optional.empty();
    }
}
