package com.example.embeddingexplorer.api;

import com.example.embeddingexplorer.search.RagSearchService;
import com.example.embeddingexplorer.search.SearchRequest;
import com.example.embeddingexplorer.search.SearchResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Simula una ricerca RAG": the retrieval step of the L18 RagService. */
@RestController
@RequestMapping(ApiPaths.V1)
class SearchController {

    private final RagSearchService search;

    SearchController(RagSearchService search) {
        this.search = search;
    }

    @PostMapping("/search")
    SearchResult search(@Valid @RequestBody SearchRequest request) {
        return search.search(request);
    }
}
