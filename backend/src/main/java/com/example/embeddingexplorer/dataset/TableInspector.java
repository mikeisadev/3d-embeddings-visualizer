package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.TableRef;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read-only queries about the database and the vector table: catalog information and cheap
 * statistics. Used by the diagnostics (which report) and by the snapshot loader (which
 * fails fast).
 *
 * <p>Every value is a bound parameter. The table name is the only text inserted in SQL and
 * it comes from validated configuration ({@link TableRef#sql()}). Catalog lookups receive
 * the raw names as parameters and quote them with {@code format('%I.%I')}.
 */
@Component
public class TableInspector {

    private static final String REGCLASS = "to_regclass(format('%I.%I', ?, ?))";

    private final JdbcClient jdbc;

    public TableInspector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Server version, database and user of the current connection. */
    public ServerInfo serverInfo() {
        return jdbc.sql("""
                        SELECT current_setting('server_version') AS server_version,
                               current_database() AS database_name,
                               current_user AS user_name
                        """)
                .query((rs, rowNum) -> new ServerInfo(
                        rs.getString("server_version"), rs.getString("database_name"), rs.getString("user_name")))
                .single();
    }

    /** The installed pgvector version, empty when the extension is missing. */
    public Optional<String> pgvectorVersion() {
        return jdbc.sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
                .query(String.class)
                .optional();
    }

    /** Whether the table exists and the current user may read it. */
    public TableAccess tableAccess(TableRef table) {
        return jdbc.sql("""
                        SELECT target.oid IS NOT NULL AS table_exists,
                               target.oid IS NOT NULL AND has_table_privilege(target.oid, 'SELECT') AS can_select
                        FROM (SELECT %s AS oid) AS target
                        """.formatted(REGCLASS))
                .params(table.schema().value(), table.table().value())
                .query((rs, rowNum) -> new TableAccess(rs.getBoolean("table_exists"), rs.getBoolean("can_select")))
                .single();
    }

    /** The columns of the table with their types. */
    public TableColumns columns(TableRef table) {
        List<TableColumns.Column> columns = jdbc.sql("""
                        SELECT a.attname AS column_name,
                               format_type(a.atttypid, a.atttypmod) AS data_type,
                               t.typname AS base_type
                        FROM pg_attribute a
                        JOIN pg_type t ON t.oid = a.atttypid
                        WHERE a.attrelid = %s AND a.attnum > 0 AND NOT a.attisdropped
                        ORDER BY a.attnum
                        """.formatted(REGCLASS))
                .params(table.schema().value(), table.table().value())
                .query((rs, rowNum) -> new TableColumns.Column(
                        rs.getString("column_name"), rs.getString("data_type"), rs.getString("base_type")))
                .list();
        return new TableColumns(columns);
    }

    /** The indexes that include the {@code embedding} column, with their operator class. */
    public List<VectorIndex> embeddingIndexes(TableRef table) {
        return jdbc.sql("""
                        SELECT index_class.relname AS index_name,
                               access_method.amname AS index_type,
                               operator_class.opcname AS operator_class
                        FROM pg_index ix
                        JOIN pg_class index_class ON index_class.oid = ix.indexrelid
                        JOIN pg_am access_method ON access_method.oid = index_class.relam
                        CROSS JOIN LATERAL unnest(ix.indkey::int2[], ix.indclass::oid[]) AS key_column(attnum, opclass)
                        JOIN pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = key_column.attnum
                        JOIN pg_opclass operator_class ON operator_class.oid = key_column.opclass
                        WHERE ix.indrelid = %s AND a.attname = 'embedding'
                        ORDER BY index_class.relname
                        """.formatted(REGCLASS))
                .params(table.schema().value(), table.table().value())
                .query((rs, rowNum) -> new VectorIndex(
                        rs.getString("index_name"), rs.getString("index_type"), rs.getString("operator_class")))
                .list();
    }

    /** Total rows and rows with a non-NULL embedding. */
    public RowCounts rowCounts(TableRef table) {
        return jdbc.sql("SELECT count(*) AS total_rows, count(embedding) AS rows_with_embedding FROM " + table.sql())
                .query((rs, rowNum) -> new RowCounts(rs.getLong("total_rows"), rs.getLong("rows_with_embedding")))
                .single();
    }

    /** How many vectors have each dimension; more than one group means mixed dimensions. */
    public List<DimensionGroup> dimensionGroups(TableRef table) {
        return jdbc.sql("""
                        SELECT vector_dims(embedding) AS dimensions, count(*) AS row_count
                        FROM %s
                        WHERE embedding IS NOT NULL
                        GROUP BY 1
                        ORDER BY 1
                        """.formatted(table.sql()))
                .query((rs, rowNum) -> new DimensionGroup(rs.getInt("dimensions"), rs.getLong("row_count")))
                .list();
    }

    /** L2 norms of a deterministic sample of vectors. */
    public NormStats normStats(TableRef table, int sampleSize) {
        return jdbc.sql("""
                        SELECT count(*) AS sample_size, min(norm) AS min_norm, avg(norm) AS avg_norm, max(norm) AS max_norm
                        FROM (
                            SELECT vector_norm(embedding) AS norm
                            FROM %s
                            WHERE embedding IS NOT NULL
                            ORDER BY md5(id::text), id::text
                            LIMIT ?
                        ) AS sample
                        """.formatted(table.sql()))
                .param(sampleSize)
                .query((rs, rowNum) -> new NormStats(
                        rs.getInt("sample_size"),
                        rs.getDouble("min_norm"),
                        rs.getDouble("avg_norm"),
                        rs.getDouble("max_norm")))
                .single();
    }

    /** The distinct top-level metadata keys (rows whose metadata is not a JSON object are ignored). */
    public List<String> metadataKeys(TableRef table) {
        return jdbc.sql("""
                        SELECT DISTINCT json_object_keys(metadata::json) AS metadata_key
                        FROM %s
                        WHERE json_typeof(metadata::json) = 'object'
                        ORDER BY 1
                        LIMIT 1000
                        """.formatted(table.sql()))
                .query(String.class)
                .list();
    }

    /**
     * A cheap fingerprint of the table content: the row count plus a hash of every id and
     * row version. {@code xmin} changes when a row is updated in place, which happens when a
     * document is re-indexed with stable ids (Spring AI upserts on {@code id}).
     */
    public String fingerprint(TableRef table) {
        return jdbc.sql("""
                        SELECT count(*) AS row_count,
                               md5(coalesce(string_agg(id::text || ':' || xmin::text, ',' ORDER BY id::text), '')) AS digest
                        FROM %s
                        """.formatted(table.sql()))
                .query((rs, rowNum) -> rs.getLong("row_count") + "-" + rs.getString("digest").substring(0, 16))
                .single();
    }

    /**
     * Connection facts.
     *
     * @param version  PostgreSQL server version
     * @param database current database
     * @param user     current user
     */
    public record ServerInfo(String version, String database, String user) {}

    /**
     * Existence and read permission of the table.
     *
     * @param exists    whether the table exists
     * @param canSelect whether the current user has {@code SELECT} on it
     */
    public record TableAccess(boolean exists, boolean canSelect) {}

    /**
     * Row counts.
     *
     * @param total         all rows
     * @param withEmbedding rows whose embedding is not NULL
     */
    public record RowCounts(long total, long withEmbedding) {

        public long withoutEmbedding() {
            return total - withEmbedding;
        }
    }

    /**
     * Vectors sharing a dimension.
     *
     * @param dimensions vector dimension
     * @param rows       how many rows have it
     */
    public record DimensionGroup(int dimensions, long rows) {}

    /**
     * L2 norm statistics of a sample.
     *
     * @param sampleSize vectors in the sample (0 when the table has no vectors)
     * @param min        smallest norm
     * @param average    average norm
     * @param max        largest norm
     */
    public record NormStats(int sampleSize, double min, double average, double max) {}
}
