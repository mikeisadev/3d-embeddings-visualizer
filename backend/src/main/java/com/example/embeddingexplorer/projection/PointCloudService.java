package com.example.embeddingexplorer.projection;

import com.example.embeddingexplorer.dataset.DatasetService;
import com.example.embeddingexplorer.dataset.DatasetSnapshot;
import org.springframework.stereotype.Service;

/** Builds the point cloud of the 3D view from the current snapshot and its projection. */
@Service
public class PointCloudService {

    private final DatasetService datasets;
    private final ProjectionService projections;

    public PointCloudService(DatasetService datasets, ProjectionService projections) {
        this.datasets = datasets;
        this.projections = projections;
    }

    public PointCloud points(boolean normalize) {
        DatasetSnapshot snapshot = datasets.currentSnapshot();
        return PointCloud.of(snapshot, projections.projectionOf(snapshot, normalize));
    }
}
