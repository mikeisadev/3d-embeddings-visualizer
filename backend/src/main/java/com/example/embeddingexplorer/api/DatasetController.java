package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.dataset.DatasetService;
import com.example.embeddingexplorer.dataset.DatasetSummary;
import com.example.embeddingexplorer.projection.PointCloud;
import com.example.embeddingexplorer.projection.PointCloudService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The dataset as a whole: its summary and the projected point cloud. */
@RestController
@RequestMapping(ApiPaths.V1)
class DatasetController {

    private final DatasetService datasets;
    private final PointCloudService pointClouds;

    DatasetController(DatasetService datasets, PointCloudService pointClouds) {
        this.datasets = datasets;
        this.pointClouds = pointClouds;
    }

    @GetMapping("/dataset")
    DatasetSummary dataset() {
        return datasets.summary();
    }

    @GetMapping("/points")
    PointCloud points(@RequestParam(defaultValue = "true") boolean normalize) {
        return pointClouds.points(normalize);
    }
}
