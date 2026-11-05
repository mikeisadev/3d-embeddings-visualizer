package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.diagnostics.DiagnosticsReport;
import com.example.embeddingexplorer.diagnostics.DiagnosticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The diagnostics checklist. Always answers 200: each check reports its own failure. */
@RestController
@RequestMapping(ApiPaths.V1 + "/diagnostics")
class DiagnosticsController {

    private final DiagnosticsService diagnostics;

    DiagnosticsController(DiagnosticsService diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping
    DiagnosticsReport diagnostics() {
        return diagnostics.run();
    }
}
