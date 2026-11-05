package com.example.embeddingexplorer.projection;

import com.example.embeddingexplorer.dataset.ChunkPoint;
import com.example.embeddingexplorer.dataset.DatasetSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response of {@code GET /api/v1/points}: everything the 3D view needs.
 *
 * @param datasetVersion    fingerprint of the data; the frontend reloads when it changes
 * @param projection        how the positions were computed and how much variance they keep
 * @param sampled           whether the points are a sample of the table
 * @param rowsWithEmbedding chunks with a vector in the table
 * @param points            one entry per loaded chunk, in reading order
 */
public record PointCloud(
        String datasetVersion,
        ProjectionInfo projection,
        boolean sampled,
        long rowsWithEmbedding,
        List<Point> points) {

    public PointCloud {
        points = List.copyOf(points);
    }

    /**
     * Description of the projection.
     *
     * @param method                 always {@code pca} (UMAP, if used, runs in the browser)
     * @param normalized             whether the vectors were L2-normalized before the PCA
     * @param dimensions             dimension of the original vectors
     * @param explainedVarianceRatio share of variance of PC1, PC2, PC3
     * @param totalExplainedVariance share kept by the three components together
     * @param iterations             power iterations used
     * @param converged              whether the tolerance was reached
     */
    public record ProjectionInfo(
            String method,
            boolean normalized,
            int dimensions,
            List<Double> explainedVarianceRatio,
            double totalExplainedVariance,
            int iterations,
            boolean converged) {}

    /**
     * One chunk in 3D. The coordinates are a projection: never compute real distances on them.
     *
     * @param id            chunk id
     * @param x             coordinate on PC1
     * @param y             coordinate on PC2
     * @param z             coordinate on PC3
     * @param file          derived file
     * @param page          derived page, or {@code null}
     * @param chunkIndex    derived chunk index, or {@code null}
     * @param preview       first characters of the text
     * @param contentLength full text length in characters
     * @param metadata      scalar metadata values
     */
    public record Point(
            String id,
            float x,
            float y,
            float z,
            String file,
            Integer page,
            Integer chunkIndex,
            String preview,
            int contentLength,
            Map<String, Object> metadata) {}

    static PointCloud of(DatasetSnapshot snapshot, PcaProjection projection) {
        PcaModel model = projection.model();
        List<Point> points = new ArrayList<>(snapshot.chunks().size());
        for (int i = 0; i < snapshot.chunks().size(); i++) {
            ChunkPoint chunk = snapshot.chunks().get(i);
            points.add(new Point(
                    chunk.id(),
                    projection.coordinate(i, 0),
                    projection.coordinate(i, 1),
                    projection.coordinate(i, 2),
                    chunk.file(),
                    chunk.page(),
                    chunk.chunkIndex(),
                    chunk.preview(),
                    chunk.contentLength(),
                    chunk.metadata()));
        }
        ProjectionInfo info = new ProjectionInfo(
                "pca",
                model.normalized(),
                model.dimensions(),
                model.explainedVarianceRatio(),
                model.totalExplainedVariance(),
                model.iterations(),
                model.converged());
        return new PointCloud(snapshot.version(), info, snapshot.sampled(), snapshot.rowsWithEmbedding(), points);
    }
}
