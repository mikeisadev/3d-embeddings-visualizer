package com.example.embeddingexplorer.config;

/**
 * The vector table the tool reads, for example {@code public.vector_store}.
 *
 * @param schema the schema identifier
 * @param table  the table identifier
 */
public record TableRef(SqlIdentifier schema, SqlIdentifier table) {

    public static TableRef of(String schema, String table) {
        return new TableRef(new SqlIdentifier(schema), new SqlIdentifier(table));
    }

    /** The quoted, schema-qualified name used in {@code FROM} clauses. */
    public String sql() {
        return schema.quoted() + "." + table.quoted();
    }

    /** The human-readable name, for example {@code public.vector_store}. */
    public String displayName() {
        return schema.value() + "." + table.value();
    }
}
