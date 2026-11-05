package com.example.embeddingexplorer.diagnostics;

import static com.example.embeddingexplorer.diagnostics.DiagnosticCheck.facts;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.dataset.ChunkFields;
import com.example.embeddingexplorer.dataset.DistanceMetric;
import com.example.embeddingexplorer.dataset.ResolvedMetric;
import com.example.embeddingexplorer.dataset.TableColumns;
import com.example.embeddingexplorer.dataset.TableInspector;
import com.example.embeddingexplorer.dataset.VectorIndex;
import com.example.embeddingexplorer.error.DatabaseErrorTranslator;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.error.WarningCode;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Runs the diagnostics checklist shown in the "Stato" section.
 *
 * <p>Checks run in a fixed order; when one fails, the checks that depend on it are
 * {@code SKIPPED}. Each check catches its own failure, so the report is always complete. A
 * stopped database costs at most one connection timeout (5 s by default).
 *
 * <p>The frontend polls this endpoint while the database is down, so failures are logged
 * only when a check changes status, not at every poll.
 */
@Service
public class DiagnosticsService {

    static final String DB_CONNECTION = "db.connection";
    static final String DB_PGVECTOR = "db.pgvector";
    static final String TABLE_EXISTS = "table.exists";
    static final String TABLE_COLUMNS = "table.columns";
    static final String TABLE_ROWS = "table.rows";
    static final String EMBEDDING_DIMENSIONS = "embedding.dimensions";
    static final String EMBEDDING_NORMS = "embedding.norms";
    static final String TABLE_INDEX = "table.index";
    static final String METADATA_KEYS = "metadata.keys";
    static final String EMBEDDING_CLIENT = "embedding.client";

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsService.class);
    private static final int NORM_SAMPLE_SIZE = 200;
    private static final double UNIT_NORM_TOLERANCE = 0.01;

    private final TableInspector inspector;
    private final TableRef table;
    private final ExplorerProperties properties;
    private final Clock clock;
    private final AtomicReference<Map<String, CheckStatus>> lastStatuses = new AtomicReference<>(Map.of());

    public DiagnosticsService(TableInspector inspector, TableRef table, ExplorerProperties properties, Clock clock) {
        this.inspector = inspector;
        this.table = table;
        this.properties = properties;
        this.clock = clock;
    }

    public DiagnosticsReport run() {
        DiagnosticCheck connection = attempt(DB_CONNECTION, connectionFacts(), this::checkConnection);
        DiagnosticCheck pgvector = after(connection, DB_PGVECTOR, this::checkPgvector);
        DiagnosticCheck exists = after(pgvector, TABLE_EXISTS, this::checkTableExists);
        DiagnosticCheck columns = after(exists, TABLE_COLUMNS, this::checkColumns);
        DiagnosticCheck rows = after(columns, TABLE_ROWS, this::checkRows);
        boolean hasRows = rows.passed() && !WarningCode.TABLE_EMPTY.name().equals(rows.code());
        DiagnosticCheck dimensions = afterRows(rows, hasRows, EMBEDDING_DIMENSIONS, this::checkDimensions);
        DiagnosticCheck norms = afterRows(rows, hasRows, EMBEDDING_NORMS, this::checkNorms);
        DiagnosticCheck index = after(columns, TABLE_INDEX, this::checkIndex);
        DiagnosticCheck metadata = afterRows(rows, hasRows, METADATA_KEYS, this::checkMetadataKeys);
        DiagnosticCheck client = checkEmbeddingClient();

        List<DiagnosticCheck> checks =
                List.of(connection, pgvector, exists, columns, rows, dimensions, norms, index, metadata, client);
        logChanges(checks);
        return DiagnosticsReport.of(checks, clock.instant());
    }

    private DiagnosticCheck checkConnection() {
        TableInspector.ServerInfo server = inspector.serverInfo();
        Map<String, Object> facts = connectionFacts();
        facts.put("serverVersion", server.version());
        facts.put("database", server.database());
        return DiagnosticCheck.ok(DB_CONNECTION, "Connected to PostgreSQL " + server.version() + ".", facts);
    }

    private DiagnosticCheck checkPgvector() {
        return inspector.pgvectorVersion()
                .map(version -> DiagnosticCheck.ok(
                        DB_PGVECTOR, "pgvector " + version + " is installed.", facts("version", version)))
                .orElseGet(() -> DiagnosticCheck.fail(
                        DB_PGVECTOR,
                        ErrorCode.PGVECTOR_MISSING,
                        "The 'vector' extension is not installed in this database.",
                        facts()));
    }

    private DiagnosticCheck checkTableExists() {
        TableInspector.TableAccess access = inspector.tableAccess(table);
        Map<String, Object> facts = facts("table", table.displayName());
        if (!access.exists()) {
            return DiagnosticCheck.fail(
                    TABLE_EXISTS, ErrorCode.TABLE_NOT_FOUND, "Table " + table.displayName() + " does not exist.", facts);
        }
        if (!access.canSelect()) {
            return DiagnosticCheck.fail(
                    TABLE_EXISTS,
                    ErrorCode.DB_PERMISSION_DENIED,
                    "The database user has no SELECT permission on " + table.displayName() + ".",
                    facts);
        }
        return DiagnosticCheck.ok(TABLE_EXISTS, "Table " + table.displayName() + " exists and is readable.", facts);
    }

    private DiagnosticCheck checkColumns() {
        TableColumns columns = inspector.columns(table);
        List<String> problems = columns.problems();
        Map<String, Object> facts = facts("columns", columns.types());
        if (!problems.isEmpty()) {
            facts.put("problems", problems);
            return DiagnosticCheck.fail(
                    TABLE_COLUMNS,
                    ErrorCode.TABLE_SCHEMA_INVALID,
                    "Unexpected table structure: " + String.join("; ", problems) + ".",
                    facts);
        }
        String embeddingType = columns.find("embedding").map(TableColumns.Column::dataType).orElse("vector");
        return DiagnosticCheck.ok(
                TABLE_COLUMNS, "Columns id, content, metadata and embedding (" + embeddingType + ") found.", facts);
    }

    private DiagnosticCheck checkRows() {
        TableInspector.RowCounts counts = inspector.rowCounts(table);
        Map<String, Object> facts = facts(
                "rows", counts.total(),
                "rowsWithEmbedding", counts.withEmbedding(),
                "rowsWithoutEmbedding", counts.withoutEmbedding());
        if (counts.total() == 0) {
            return DiagnosticCheck.warn(TABLE_ROWS, WarningCode.TABLE_EMPTY, "The table has no rows.", facts);
        }
        if (counts.withoutEmbedding() > 0) {
            return DiagnosticCheck.warn(
                    TABLE_ROWS,
                    WarningCode.NULL_EMBEDDINGS_SKIPPED,
                    counts.withoutEmbedding() + " of " + counts.total() + " rows have a NULL embedding and are skipped.",
                    facts);
        }
        return DiagnosticCheck.ok(TABLE_ROWS, counts.total() + " rows.", facts);
    }

    private DiagnosticCheck checkDimensions() {
        List<TableInspector.DimensionGroup> groups = inspector.dimensionGroups(table);
        if (groups.isEmpty()) {
            return DiagnosticCheck.skipped(EMBEDDING_DIMENSIONS, "No row has an embedding.");
        }
        List<Map<String, Object>> groupFacts = groups.stream()
                .map(group -> facts("dimensions", group.dimensions(), "rows", group.rows()))
                .toList();
        List<Integer> found = groups.stream().map(TableInspector.DimensionGroup::dimensions).toList();
        if (groups.size() > 1) {
            return DiagnosticCheck.warn(
                    EMBEDDING_DIMENSIONS,
                    WarningCode.DIMENSIONS_MIXED,
                    "Vectors have different dimensions " + found + ": the 3D view cannot mix them.",
                    facts("groups", groupFacts));
        }
        return DiagnosticCheck.ok(
                EMBEDDING_DIMENSIONS,
                "All vectors have " + found.getFirst() + " dimensions.",
                facts("dimensions", found.getFirst(), "groups", groupFacts));
    }

    private DiagnosticCheck checkNorms() {
        TableInspector.NormStats stats = inspector.normStats(table, NORM_SAMPLE_SIZE);
        if (stats.sampleSize() == 0) {
            return DiagnosticCheck.skipped(EMBEDDING_NORMS, "No row has an embedding.");
        }
        boolean unitLength = Math.abs(stats.min() - 1) <= UNIT_NORM_TOLERANCE
                && Math.abs(stats.max() - 1) <= UNIT_NORM_TOLERANCE;
        return DiagnosticCheck.ok(
                EMBEDDING_NORMS,
                String.format(
                        Locale.ROOT,
                        "L2 norm of %d sampled vectors: min %.4f, average %.4f, max %.4f.",
                        stats.sampleSize(),
                        stats.min(),
                        stats.average(),
                        stats.max()),
                facts(
                        "sampleSize", stats.sampleSize(),
                        "min", stats.min(),
                        "average", stats.average(),
                        "max", stats.max(),
                        "unitLength", unitLength));
    }

    private DiagnosticCheck checkIndex() {
        List<VectorIndex> indexes = inspector.embeddingIndexes(table);
        ResolvedMetric metric = ResolvedMetric.resolve(properties.distanceMetric(), indexes);
        Optional<VectorIndex> index = indexes.stream()
                .filter(candidate -> candidate.metric().isPresent())
                .findFirst()
                .or(() -> indexes.stream().findFirst());
        Map<String, Object> facts = facts(
                "indexName", index.map(VectorIndex::name).orElse(null),
                "indexType", index.map(VectorIndex::type).orElse(null),
                "operatorClass", index.map(VectorIndex::operatorClass).orElse(null),
                "metric", metric.metric().name(),
                "metricSource", metric.source().name());
        String metricText = metric.metric().name() + " (" + metric.source().name().toLowerCase(Locale.ROOT) + ")";
        if (index.isEmpty()) {
            return DiagnosticCheck.warn(
                    TABLE_INDEX,
                    WarningCode.INDEX_MISSING,
                    "No index on embedding: searches scan the whole table, slower but still correct. Metric: "
                            + metricText + ".",
                    facts);
        }
        Optional<DistanceMetric> indexMetric = index.get().metric();
        if (indexMetric.isPresent() && indexMetric.get() != metric.metric()) {
            return DiagnosticCheck.warn(
                    TABLE_INDEX,
                    WarningCode.METRIC_DIFFERS_FROM_INDEX,
                    "The configured metric " + metric.metric().name() + " differs from the index operator class "
                            + index.get().operatorClass() + ": the index cannot speed up the searches.",
                    facts);
        }
        return DiagnosticCheck.ok(
                TABLE_INDEX,
                "Index " + index.get().name() + " (" + index.get().type() + ", " + index.get().operatorClass()
                        + "). Metric: " + metricText + ".",
                facts);
    }

    private DiagnosticCheck checkMetadataKeys() {
        List<String> keys = inspector.metadataKeys(table);
        Optional<String> fileKey = ChunkFields.FILE_KEYS.stream().filter(keys::contains).findFirst();
        Map<String, Object> facts = facts("keys", keys, "fileKey", fileKey.orElse(null));
        if (fileKey.isEmpty()) {
            return DiagnosticCheck.warn(
                    METADATA_KEYS,
                    WarningCode.FILE_KEY_MISSING,
                    "No file key (" + String.join(", ", ChunkFields.FILE_KEYS) + ") found: chunks cannot be grouped by file.",
                    facts);
        }
        return DiagnosticCheck.ok(
                METADATA_KEYS, keys.size() + " metadata keys; file names come from '" + fileKey.get() + "'.", facts);
    }

    private DiagnosticCheck checkEmbeddingClient() {
        ExplorerProperties.Embedding embedding = properties.embedding();
        Map<String, Object> facts = facts(
                "enabled", embedding.enabled(),
                "baseUrl", embedding.baseUrl(),
                "model", embedding.model(),
                "apiKeyPresent", embedding.hasApiKey());
        if (!embedding.enabled()) {
            return new DiagnosticCheck(
                    EMBEDDING_CLIENT,
                    CheckStatus.SKIPPED,
                    "The RAG simulation is disabled (VIZ_EMBEDDING_ENABLED=false).",
                    ErrorCode.EMBEDDING_DISABLED.name(),
                    facts);
        }
        if (!embedding.hasApiKey() && isOpenAi(embedding.baseUrl())) {
            return DiagnosticCheck.warn(
                    EMBEDDING_CLIENT,
                    WarningCode.EMBEDDING_KEY_MISSING,
                    "OpenAI requires an API key (VIZ_EMBEDDING_API_KEY).",
                    facts);
        }
        return DiagnosticCheck.ok(
                EMBEDDING_CLIENT,
                "Model " + embedding.model() + " at " + embedding.baseUrl() + ". Use the live test to verify it.",
                facts);
    }

    private DiagnosticCheck after(DiagnosticCheck prerequisite, String id, Supplier<DiagnosticCheck> check) {
        return prerequisite.passed()
                ? attempt(id, Map.of(), check)
                : DiagnosticCheck.skipped(id, "Skipped because " + prerequisite.id() + " did not pass.");
    }

    private DiagnosticCheck afterRows(
            DiagnosticCheck rows, boolean hasRows, String id, Supplier<DiagnosticCheck> check) {
        if (rows.passed() && !hasRows) {
            return DiagnosticCheck.skipped(id, "Skipped because the table has no rows.");
        }
        return after(rows, id, check);
    }

    /** Runs a check, turning any failure into a {@code FAIL} result with a stable code. */
    private DiagnosticCheck attempt(String id, Map<String, Object> factsOnFailure, Supplier<DiagnosticCheck> check) {
        try {
            return check.get();
        } catch (ExplorerException failure) {
            return DiagnosticCheck.fail(id, failure.code(), failure.getMessage(), factsOnFailure);
        } catch (DataAccessException failure) {
            return DatabaseErrorTranslator.translate(failure)
                    .map(error -> DiagnosticCheck.fail(id, error.code(), error.getMessage(), factsOnFailure))
                    .orElseGet(() -> unexpected(id, failure));
        } catch (RuntimeException failure) {
            return unexpected(id, failure);
        }
    }

    private static DiagnosticCheck unexpected(String id, RuntimeException failure) {
        log.error("Diagnostics check {} failed unexpectedly", id, failure);
        return DiagnosticCheck.fail(
                id, ErrorCode.INTERNAL_ERROR, "Unexpected error while running the check.", Map.of());
    }

    /** URL and user, never the password; the query string is dropped because it may hold credentials. */
    private Map<String, Object> connectionFacts() {
        String url = properties.db().url();
        int query = url.indexOf('?');
        return facts("url", query < 0 ? url : url.substring(0, query), "user", properties.db().user());
    }

    private static boolean isOpenAi(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return host != null && (host.equals("openai.com") || host.endsWith(".openai.com"));
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
    }

    private void logChanges(List<DiagnosticCheck> checks) {
        Map<String, CheckStatus> current = new LinkedHashMap<>();
        checks.forEach(check -> current.put(check.id(), check.status()));
        Map<String, CheckStatus> previous = lastStatuses.getAndSet(current);
        for (DiagnosticCheck check : checks) {
            CheckStatus before = previous.get(check.id());
            boolean firstOk = before == null && check.status() == CheckStatus.OK;
            // SKIPPED is only a consequence of another check, already logged.
            if (check.status() == before || firstOk || check.status() == CheckStatus.SKIPPED) {
                continue;
            }
            if (check.status() == CheckStatus.FAIL) {
                log.warn("Diagnostics: {} is FAIL ({}): {}", check.id(), check.code(), check.detail());
            } else {
                log.info("Diagnostics: {} is {}", check.id(), check.status());
            }
        }
    }
}
