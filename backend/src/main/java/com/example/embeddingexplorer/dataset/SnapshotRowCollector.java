package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.error.WarningCode;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Streams the rows of the snapshot query and turns them into chunks and vectors, tolerating
 * dirty data: NULL content becomes an empty string and NULL or non-object metadata becomes
 * {@code {}}; both are counted as warnings. The full text is not kept, only its preview.
 */
final class SnapshotRowCollector implements RowCallbackHandler {

    private final JsonMapper jsonMapper;
    private final int dimensions;
    private final List<Row> rows = new ArrayList<>();
    private long nullContent;
    private long invalidMetadata;

    SnapshotRowCollector(JsonMapper jsonMapper, int dimensions) {
        this.jsonMapper = jsonMapper;
        this.dimensions = dimensions;
    }

    @Override
    public void processRow(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("id");
        String content = resultSet.getString("content");
        if (content == null) {
            nullContent++;
            content = "";
        }
        JsonNode metadata = parseMetadata(resultSet.getString("metadata"));
        float[] vector = toFloats(id, resultSet.getArray("embedding"));
        ChunkPoint chunk = new ChunkPoint(
                id,
                ChunkFields.file(metadata),
                ChunkFields.page(metadata),
                ChunkFields.chunkIndex(metadata),
                TextPreview.preview(content),
                TextPreview.length(content),
                ChunkFields.scalarMetadata(metadata));
        rows.add(new Row(chunk, metadata, vector));
    }

    /** Sorts the rows in reading order and computes the per-file and per-key statistics. */
    Result result() {
        rows.sort(Comparator.comparing(Row::chunk, ChunkFields.READING_ORDER));
        MetadataStatsCollector metadataStats = new MetadataStatsCollector();
        Map<String, Integer> chunksPerFile = new LinkedHashMap<>();
        for (Row row : rows) {
            metadataStats.add(row.metadata());
            chunksPerFile.merge(row.chunk().file(), 1, Integer::sum);
        }
        List<DatasetSnapshot.FileCount> files = chunksPerFile.entrySet().stream()
                .map(entry -> new DatasetSnapshot.FileCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(DatasetSnapshot.FileCount::chunks)
                        .reversed()
                        .thenComparing(DatasetSnapshot.FileCount::file))
                .toList();
        List<DatasetSnapshot.Warning> warnings = new ArrayList<>();
        if (invalidMetadata > 0) {
            warnings.add(new DatasetSnapshot.Warning(WarningCode.METADATA_NOT_OBJECT, invalidMetadata));
        }
        if (nullContent > 0) {
            warnings.add(new DatasetSnapshot.Warning(WarningCode.CONTENT_NULL, nullContent));
        }
        return new Result(
                rows.stream().map(Row::chunk).toList(),
                VectorMatrix.of(rows.stream().map(Row::vector).toArray(float[][]::new)),
                metadataStats.stats(),
                files,
                warnings);
    }

    private JsonNode parseMetadata(String json) {
        if (json != null) {
            try {
                JsonNode node = jsonMapper.readTree(json);
                if (node.isObject()) {
                    return node;
                }
            } catch (JacksonException invalidJson) {
                // A json column always holds valid JSON; a text column may not. Treated like NULL.
            }
        }
        invalidMetadata++;
        return JsonNodeFactory.instance.objectNode();
    }

    private float[] toFloats(String id, Array array) throws SQLException {
        try {
            Object values = array.getArray();
            if (!(values instanceof Float[] boxed) || boxed.length != dimensions) {
                throw new ExplorerException(
                        ErrorCode.DIMENSION_MISMATCH,
                        "Chunk %s does not have %d dimensions like the rest of the table.".formatted(id, dimensions));
            }
            float[] vector = new float[boxed.length];
            for (int i = 0; i < boxed.length; i++) {
                vector[i] = boxed[i];
            }
            return vector;
        } finally {
            array.free();
        }
    }

    private record Row(ChunkPoint chunk, JsonNode metadata, float[] vector) {}

    /**
     * The collected data, in reading order.
     *
     * @param chunks       chunk descriptions
     * @param vectors      vectors, same order as {@code chunks}
     * @param metadataKeys metadata statistics
     * @param files        chunks per file
     * @param warnings     dirty-data warnings
     */
    record Result(
            List<ChunkPoint> chunks,
            VectorMatrix vectors,
            List<MetadataKeyStats> metadataKeys,
            List<DatasetSnapshot.FileCount> files,
            List<DatasetSnapshot.Warning> warnings) {}
}
