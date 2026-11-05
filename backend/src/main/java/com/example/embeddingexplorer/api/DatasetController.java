package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.dataset.DatasetService;
import com.example.embeddingexplorer.dataset.DatasetSnapshot;
import com.example.embeddingexplorer.dataset.DatasetSummary;
import com.example.embeddingexplorer.dataset.VectorMatrix;
import com.example.embeddingexplorer.projection.PointCloud;
import com.example.embeddingexplorer.projection.PointCloudService;
import com.example.embeddingexplorer.search.NeighborSample;
import com.example.embeddingexplorer.search.NeighborSampleService;
import com.example.embeddingexplorer.search.NeighborService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** The dataset as a whole: its summary, the projected point cloud, the raw vectors and a neighbor sample. */
@RestController
@RequestMapping(ApiPaths.V1)
class DatasetController {

    static final String ROWS_HEADER = "X-Rows";
    static final String DIMENSIONS_HEADER = "X-Dimensions";
    static final String VERSION_HEADER = "X-Dataset-Version";

    private final DatasetService datasets;
    private final PointCloudService pointClouds;
    private final NeighborSampleService neighborSamples;

    DatasetController(
            DatasetService datasets, PointCloudService pointClouds, NeighborSampleService neighborSamples) {
        this.datasets = datasets;
        this.pointClouds = pointClouds;
        this.neighborSamples = neighborSamples;
    }

    @GetMapping("/dataset")
    DatasetSummary dataset() {
        return datasets.summary();
    }

    @GetMapping("/points")
    PointCloud points(@RequestParam(defaultValue = "true") boolean normalize) {
        return pointClouds.points(normalize);
    }

    /**
     * The snapshot vectors for the UMAP worker of the frontend: float32 little-endian,
     * row-major, in the same order as {@code /points}. Streamed, so a large snapshot is never
     * copied in memory; {@code DATASET_CHANGED} (409) when the version is not the current one.
     */
    @GetMapping("/vectors")
    ResponseEntity<StreamingResponseBody> vectors(@RequestParam @NotBlank @Size(max = 64) String datasetVersion) {
        DatasetSnapshot snapshot = datasets.snapshotOfVersion(datasetVersion);
        VectorMatrix vectors = snapshot.vectors();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(vectors.byteSize())
                .header(ROWS_HEADER, String.valueOf(vectors.rows()))
                .header(DIMENSIONS_HEADER, String.valueOf(vectors.dimensions()))
                .header(VERSION_HEADER, snapshot.version())
                .body(vectors::writeLittleEndian);
    }

    @GetMapping("/neighbors/sample")
    NeighborSample neighborSample(
            @RequestParam(defaultValue = "200") @Min(NeighborSampleService.MIN_SIZE) @Max(NeighborSampleService.MAX_SIZE)
                    int size,
            @RequestParam(defaultValue = "10") @Min(1) @Max(NeighborService.MAX_K) int k) {
        return neighborSamples.sample(size, k);
    }
}
