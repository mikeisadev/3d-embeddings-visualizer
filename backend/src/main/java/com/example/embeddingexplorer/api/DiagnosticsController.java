package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.diagnostics.DiagnosticsReport;
import com.example.embeddingexplorer.diagnostics.DiagnosticsService;
import com.example.embeddingexplorer.embedding.EmbeddingLiveTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The diagnostics checklist (always 200: each check reports its own failure) and the live test
 * of the embedding provider (a real call, so only on explicit request and rate limited).
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/diagnostics")
class DiagnosticsController {

    private final DiagnosticsService diagnostics;
    private final EmbeddingLiveTest embeddingTest;

    DiagnosticsController(DiagnosticsService diagnostics, EmbeddingLiveTest embeddingTest) {
        this.diagnostics = diagnostics;
        this.embeddingTest = embeddingTest;
    }

    @GetMapping
    DiagnosticsReport diagnostics() {
        return diagnostics.run();
    }

    @PostMapping("/embedding-test")
    EmbeddingLiveTest.Result embeddingTest() {
        return embeddingTest.run();
    }
}
