package com.example.embeddingexplorer.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The live test of the embedding provider, run only when a user asks for it because it is a
 * real (possibly paid) call. It shares the rate limit of the RAG search.
 */
@Service
public class EmbeddingLiveTest {

    static final String PROBE_TEXT = "Embedding Explorer 3D: prova di connessione al modello di embedding.";

    private static final Logger log = LoggerFactory.getLogger(EmbeddingLiveTest.class);

    private final EmbeddingClient embeddings;
    private final EmbeddingRateLimiter rateLimiter;

    public EmbeddingLiveTest(EmbeddingClient embeddings, EmbeddingRateLimiter rateLimiter) {
        this.embeddings = embeddings;
        this.rateLimiter = rateLimiter;
    }

    public Result run() {
        embeddings.requireEnabled();
        rateLimiter.acquire();
        EmbeddingResult result = embeddings.embed(PROBE_TEXT);
        log.info("Embedding live test: model {}, {} dimensions, {} ms", result.model(), result.vector().length,
                result.latencyMillis());
        return new Result(result.model(), result.vector().length, result.latencyMillis());
    }

    /**
     * Response of {@code POST /api/v1/diagnostics/embedding-test}.
     *
     * @param model      model name reported by the provider
     * @param dimensions dimension of the returned vector
     * @param latencyMs  how long the provider took
     */
    public record Result(String model, int dimensions, long latencyMs) {}
}
