package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.dataset.ChunkDetails;
import com.example.embeddingexplorer.dataset.ChunkMatches;
import com.example.embeddingexplorer.dataset.ChunkService;
import com.example.embeddingexplorer.search.CompareService;
import com.example.embeddingexplorer.search.Comparison;
import com.example.embeddingexplorer.search.NeighborService;
import com.example.embeddingexplorer.search.Neighbors;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Single chunks: details, real neighbors, comparisons and full-text matches. */
@RestController
@RequestMapping(ApiPaths.V1)
class ChunkController {

    private static final int MAX_ID_LENGTH = 256;

    private final ChunkService chunks;
    private final NeighborService neighbors;
    private final CompareService comparisons;

    ChunkController(ChunkService chunks, NeighborService neighbors, CompareService comparisons) {
        this.chunks = chunks;
        this.neighbors = neighbors;
        this.comparisons = comparisons;
    }

    @GetMapping("/chunks/{id}")
    ChunkDetails chunk(@PathVariable @NotBlank @Size(max = MAX_ID_LENGTH) String id) {
        return chunks.details(id);
    }

    @GetMapping("/chunks/{id}/neighbors")
    Neighbors neighbors(
            @PathVariable @NotBlank @Size(max = MAX_ID_LENGTH) String id,
            @RequestParam(defaultValue = "10") @Min(1) @Max(NeighborService.MAX_K) int k) {
        return neighbors.neighbors(id, k);
    }

    @GetMapping("/chunks")
    ChunkMatches matching(@RequestParam @NotBlank @Size(min = 2, max = 200) String contains) {
        return chunks.containing(contains);
    }

    @GetMapping("/compare")
    Comparison compare(
            @RequestParam @NotBlank @Size(max = MAX_ID_LENGTH) String a,
            @RequestParam @NotBlank @Size(max = MAX_ID_LENGTH) String b) {
        return comparisons.compare(a, b);
    }
}
