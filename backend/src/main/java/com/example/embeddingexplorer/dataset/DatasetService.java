package com.example.embeddingexplorer.dataset;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serves the current {@link DatasetSnapshot}, reloading it only when the table changes.
 *
 * <p>Every call first checks that the table is readable (so the error is as precise as in
 * the diagnostics: {@code PGVECTOR_MISSING} before {@code TABLE_NOT_FOUND}), then computes the
 * cheap table fingerprint; the cached snapshot is reused while it matches. Loading is
 * single-flight: concurrent requests wait for the same load instead of reading the table
 * several times. Only the latest snapshot is kept.
 */
@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final SnapshotLoader loader;
    private final VectorTableGuard guard;
    private final TableInspector inspector;
    private final TableRef table;
    private final ExplorerProperties properties;
    private final ReentrantLock loadLock = new ReentrantLock();
    private volatile DatasetSnapshot cached;

    public DatasetService(
            SnapshotLoader loader,
            VectorTableGuard guard,
            TableInspector inspector,
            TableRef table,
            ExplorerProperties properties) {
        this.loader = loader;
        this.guard = guard;
        this.inspector = inspector;
        this.table = table;
        this.properties = properties;
    }

    /** The snapshot matching the current content of the table. */
    public DatasetSnapshot currentSnapshot() {
        guard.requireReadableVectorTable();
        String version = inspector.fingerprint(table);
        DatasetSnapshot snapshot = cached;
        if (snapshot != null && snapshot.version().equals(version)) {
            return snapshot;
        }
        loadLock.lock();
        try {
            snapshot = cached;
            if (snapshot != null && snapshot.version().equals(version)) {
                return snapshot;
            }
            long start = System.nanoTime();
            DatasetSnapshot loaded = loader.load();
            cached = loaded;
            log.info(
                    "Loaded snapshot {} of {}: {} chunks, {} dimensions, sampled={} in {} ms",
                    loaded.version(),
                    table.displayName(),
                    loaded.chunks().size(),
                    loaded.dimensions(),
                    loaded.sampled(),
                    (System.nanoTime() - start) / 1_000_000);
            return loaded;
        } finally {
            loadLock.unlock();
        }
    }

    /**
     * The current snapshot, only if it is still the version the client expects. Data that must
     * line up with what the client already has (for example the raw vectors, row by row with
     * the points on screen) fails with {@code DATASET_CHANGED} instead of silently mixing two
     * versions of the table.
     */
    public DatasetSnapshot snapshotOfVersion(String expectedVersion) {
        DatasetSnapshot snapshot = currentSnapshot();
        if (!snapshot.version().equals(expectedVersion)) {
            throw new ExplorerException(
                    ErrorCode.DATASET_CHANGED,
                    "The dataset changed: the current version is " + snapshot.version() + ".",
                    Map.of("currentVersion", snapshot.version()));
        }
        return snapshot;
    }

    public DatasetSummary summary() {
        return DatasetSummary.of(currentSnapshot(), table, properties.maxPoints());
    }
}
