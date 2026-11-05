package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.dataset.DatasetService;
import com.example.embeddingexplorer.dataset.DatasetSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What is in the vector table: counts, dimensions, metric, index, files and metadata keys. */
@RestController
@RequestMapping(ApiPaths.V1)
class DatasetController {

    private final DatasetService datasets;

    DatasetController(DatasetService datasets) {
        this.datasets = datasets;
    }

    @GetMapping("/dataset")
    DatasetSummary dataset() {
        return datasets.summary();
    }
}
