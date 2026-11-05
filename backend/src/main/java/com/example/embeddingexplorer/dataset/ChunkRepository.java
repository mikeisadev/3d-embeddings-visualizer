package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Reads single chunks from the vector table. Ids are opaque strings (UUID in L17, but Spring
 * AI also allows text and numbers), so they are compared as {@code id::text = ?}.
 */
@Component
public class ChunkRepository {

    private final JdbcClient jdbc;
    private final TableRef table;
    private final JsonMapper jsonMapper;

    public ChunkRepository(JdbcClient jdbc, TableRef table, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.table = table;
        this.jsonMapper = jsonMapper;
    }

    public Optional<ChunkDetails> findById(String id) {
        return jdbc.sql("""
                        SELECT id::text AS id, content::text AS content, metadata::text AS metadata,
                               embedding::real[] AS embedding
                        FROM %s
                        WHERE id::text = ?
                        """.formatted(table.sql()))
                .param(id)
                .query((rs, rowNum) -> details(rs))
                .optional();
    }

    /** The pgvector text form of the chunk embedding, ready to be bound as {@code CAST(? AS vector)}. */
    public Optional<String> findVectorLiteral(String id) {
        return jdbc.sql("SELECT embedding::text FROM %s WHERE id::text = ? AND embedding IS NOT NULL"
                        .formatted(table.sql()))
                .param(id)
                .query(String.class)
                .optional();
    }

    /** The ids among {@code ids} that exist in the table. */
    public List<String> existingIds(List<String> ids) {
        return jdbc.sql("SELECT id::text FROM %s WHERE id::text = ANY (?)".formatted(table.sql()))
                .param(ids.toArray(String[]::new))
                .query(String.class)
                .list();
    }

    /** Chunks whose full text contains {@code text}, ignoring case; at most {@code limit} + 1 ids. */
    public ChunkMatches idsContaining(String text, int limit) {
        List<String> ids = jdbc.sql("""
                        SELECT id::text
                        FROM %s
                        WHERE embedding IS NOT NULL AND strpos(lower(content), lower(?)) > 0
                        ORDER BY id::text
                        LIMIT ?
                        """.formatted(table.sql()))
                .params(text, limit + 1)
                .query(String.class)
                .list();
        boolean truncated = ids.size() > limit;
        return new ChunkMatches(text, truncated ? ids.subList(0, limit) : ids, truncated);
    }

    private ChunkDetails details(ResultSet rs) throws SQLException {
        String content = rs.getString("content");
        JsonNode metadata = parseMetadata(rs.getString("metadata"));
        float[] embedding = toFloats(rs.getArray("embedding"));
        return new ChunkDetails(
                rs.getString("id"),
                ChunkFields.file(metadata),
                ChunkFields.page(metadata),
                ChunkFields.chunkIndex(metadata),
                content == null ? "" : content,
                metadata,
                embedding,
                embedding == null ? null : VectorStats.of(embedding));
    }

    private JsonNode parseMetadata(String json) {
        if (json == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode node = jsonMapper.readTree(json);
            return node.isObject() ? node : JsonNodeFactory.instance.objectNode();
        } catch (JacksonException invalidJson) {
            throw new ExplorerException(
                    ErrorCode.TABLE_SCHEMA_INVALID, "The metadata of a chunk is not valid JSON.", invalidJson);
        }
    }

    private static float[] toFloats(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        try {
            Float[] boxed = (Float[]) array.getArray();
            float[] vector = new float[boxed.length];
            for (int i = 0; i < boxed.length; i++) {
                vector[i] = boxed[i];
            }
            return vector;
        } finally {
            array.free();
        }
    }
}
