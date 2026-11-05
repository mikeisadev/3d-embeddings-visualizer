package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Fails fast, with a precise error code, when the configured table cannot be read as a
 * Spring AI vector table. Without this check a wrong column type would surface as a generic
 * SQL error (for example "function vector_dims(text) does not exist").
 */
@Component
public class VectorTableGuard {

    private final TableInspector inspector;
    private final TableRef table;

    public VectorTableGuard(TableInspector inspector, TableRef table) {
        this.inspector = inspector;
        this.table = table;
    }

    public void requireReadableVectorTable() {
        if (inspector.pgvectorVersion().isEmpty()) {
            throw new ExplorerException(
                    ErrorCode.PGVECTOR_MISSING, "The pgvector extension ('vector') is not installed in the database.");
        }
        TableInspector.TableAccess access = inspector.tableAccess(table);
        if (!access.exists()) {
            throw new ExplorerException(
                    ErrorCode.TABLE_NOT_FOUND, "Table " + table.displayName() + " does not exist.");
        }
        if (!access.canSelect()) {
            throw new ExplorerException(
                    ErrorCode.DB_PERMISSION_DENIED,
                    "The database user has no SELECT permission on " + table.displayName() + ".");
        }
        List<String> problems = inspector.columns(table).problems();
        if (!problems.isEmpty()) {
            throw new ExplorerException(
                    ErrorCode.TABLE_SCHEMA_INVALID,
                    "Table " + table.displayName() + " is not a vector table: " + String.join("; ", problems) + ".",
                    Map.of("problems", problems));
        }
    }
}
