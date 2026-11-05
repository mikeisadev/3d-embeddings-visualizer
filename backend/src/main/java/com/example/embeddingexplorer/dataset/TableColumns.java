package com.example.embeddingexplorer.dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The columns of the vector table and the rules a Spring AI {@code PgVectorStore} table must
 * satisfy: {@code id}, {@code content}, {@code metadata} (json or jsonb) and {@code embedding}
 * (pgvector {@code vector}). The id type is free: Spring AI also supports text and numeric ids.
 *
 * @param columns the table columns in declaration order
 */
public record TableColumns(List<Column> columns) {

    private static final List<String> REQUIRED = List.of("id", "content", "metadata", "embedding");
    private static final Set<String> JSON_TYPES = Set.of("json", "jsonb");

    /**
     * One column.
     *
     * @param name     column name
     * @param dataType full type, for example {@code vector(1536)}
     * @param baseType type name without modifiers, for example {@code vector}
     */
    public record Column(String name, String dataType, String baseType) {}

    public TableColumns {
        columns = List.copyOf(columns);
    }

    public Optional<Column> find(String name) {
        return columns.stream().filter(column -> column.name().equals(name)).findFirst();
    }

    /** Human-readable (English) violations of the expected structure; empty when the table is valid. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        REQUIRED.stream()
                .filter(name -> find(name).isEmpty())
                .forEach(name -> problems.add("missing column '" + name + "'"));
        find("metadata")
                .filter(column -> !JSON_TYPES.contains(column.baseType()))
                .ifPresent(column -> problems.add(
                        "column 'metadata' has type " + column.dataType() + ", expected json or jsonb"));
        find("embedding")
                .filter(column -> !"vector".equals(column.baseType()))
                .ifPresent(column -> problems.add(
                        "column 'embedding' has type " + column.dataType() + ", expected vector"));
        return problems;
    }

    /** Column name to full type, in declaration order. */
    public Map<String, String> types() {
        Map<String, String> types = new LinkedHashMap<>();
        columns.forEach(column -> types.put(column.name(), column.dataType()));
        return types;
    }
}
