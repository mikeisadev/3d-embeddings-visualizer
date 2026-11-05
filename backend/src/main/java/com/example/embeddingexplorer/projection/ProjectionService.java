package com.example.embeddingexplorer.projection;

import com.example.embeddingexplorer.dataset.DatasetSnapshot;
import com.example.embeddingexplorer.dataset.DistanceMetric;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Computes and caches the PCA projection of the current snapshot, one per value of the
 * normalize flag. The cache follows the snapshot: when a new {@code datasetVersion} arrives,
 * the projections of the previous one are dropped.
 *
 * <p>Normalizing only makes sense for the cosine metric (cosine ignores the length of the
 * vectors); with L2 or inner product the raw vectors are projected.
 */
@Service
public class ProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Normalization, PcaProjection> projections = new EnumMap<>(Normalization.class);
    private String cachedVersion;

    /** Whether the vectors are L2-normalized before the PCA. */
    enum Normalization {
        NORMALIZED,
        RAW
    }

    /** The projection of {@code snapshot}, computed once per version and normalize flag. */
    public PcaProjection projectionOf(DatasetSnapshot snapshot, boolean normalizeRequested) {
        boolean normalize = effectiveNormalize(snapshot, normalizeRequested);
        Normalization key = normalize ? Normalization.NORMALIZED : Normalization.RAW;
        lock.lock();
        try {
            if (!snapshot.version().equals(cachedVersion)) {
                projections.clear();
                cachedVersion = snapshot.version();
            }
            return projections.computeIfAbsent(key, ignored -> compute(snapshot, normalize));
        } finally {
            lock.unlock();
        }
    }

    /** The normalize flag actually applied: only with the cosine metric. */
    public static boolean effectiveNormalize(DatasetSnapshot snapshot, boolean normalizeRequested) {
        return normalizeRequested && snapshot.metric().metric() == DistanceMetric.COSINE;
    }

    private static PcaProjection compute(DatasetSnapshot snapshot, boolean normalize) {
        long start = System.nanoTime();
        PcaProjection projection = Pca.fit(snapshot.vectors(), normalize);
        PcaModel model = projection.model();
        long millis = (System.nanoTime() - start) / 1_000_000;
        if (model.converged()) {
            log.info(
                    "PCA of {} vectors x {} dimensions (normalized={}) in {} ms, {} iterations",
                    projection.size(),
                    model.dimensions(),
                    normalize,
                    millis,
                    model.iterations());
        } else {
            log.warn(
                    "PCA did not reach the tolerance in {} iterations ({} ms); using the best estimate",
                    model.iterations(),
                    millis);
        }
        return projection;
    }
}
