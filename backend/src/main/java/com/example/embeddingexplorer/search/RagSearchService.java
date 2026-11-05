package com.example.embeddingexplorer.search;

import com.example.embeddingexplorer.config.TableRef;
import com.example.embeddingexplorer.dataset.DatasetService;
import com.example.embeddingexplorer.dataset.DatasetSnapshot;
import com.example.embeddingexplorer.dataset.DistanceMetric;
import com.example.embeddingexplorer.dataset.PgVectors;
import com.example.embeddingexplorer.embedding.EmbeddingClient;
import com.example.embeddingexplorer.embedding.EmbeddingRateLimiter;
import com.example.embeddingexplorer.embedding.EmbeddingResult;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.projection.PcaModel;
import com.example.embeddingexplorer.projection.ProjectionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simulates the retrieval step of the L18 {@code RagService}: the question becomes an
 * embedding, the database returns the top-K closest chunks, and the similarity threshold
 * decides which of them enter the context.
 *
 * <p>The query is the one Spring AI {@code PgVectorStore} runs (same distance, same index),
 * except that the threshold is not applied in SQL: every top-K chunk comes back with
 * {@code passesThreshold}, so the students see what the threshold cuts.
 */
@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final EmbeddingClient embeddings;
    private final EmbeddingRateLimiter rateLimiter;
    private final DatasetService datasets;
    private final ProjectionService projections;
    private final SimilarChunks similarChunks;
    private final TableRef table;

    public RagSearchService(
            EmbeddingClient embeddings,
            EmbeddingRateLimiter rateLimiter,
            DatasetService datasets,
            ProjectionService projections,
            SimilarChunks similarChunks,
            TableRef table) {
        this.embeddings = embeddings;
        this.rateLimiter = rateLimiter;
        this.datasets = datasets;
        this.projections = projections;
        this.similarChunks = similarChunks;
        this.table = table;
    }

    public SearchResult search(SearchRequest request) {
        embeddings.requireEnabled();
        DatasetSnapshot snapshot = datasets.currentSnapshot();
        PcaModel model = projections.projectionOf(snapshot, request.normalize()).model();
        rateLimiter.acquire();

        log.info(
                "RAG search: question of {} characters, topK={}, threshold={}",
                request.question().length(),
                request.topK(),
                request.similarityThreshold());
        EmbeddingResult question = embeddings.embed(request.question());
        requireSameDimensions(question, snapshot);

        DistanceMetric metric = snapshot.metric().metric();
        double distanceThreshold = 1.0 - request.similarityThreshold();
        List<SimilarChunks.Row> rows =
                similarChunks.topK(metric, PgVectors.literal(question.vector()), null, request.topK());
        List<SearchResult.Match> matches = new ArrayList<>(rows.size());
        for (SimilarChunks.Row row : rows) {
            Double distance = row.distance();
            matches.add(new SearchResult.Match(
                    matches.size() + 1,
                    row.id(),
                    distance,
                    distance == null ? null : metric.similarity(distance),
                    distance != null && distance < distanceThreshold,
                    row.file(),
                    row.page(),
                    row.chunkIndex(),
                    row.preview()));
        }

        float[] point = model.project(question.vector());
        return new SearchResult(
                snapshot.version(),
                question.model(),
                question.latencyMillis(),
                question.vector().length,
                snapshot.metric(),
                request.topK(),
                request.similarityThreshold(),
                distanceThreshold,
                model.normalized(),
                new SearchResult.QueryPoint(point[0], point[1], point[2]),
                matches,
                metric.springAiSearchSql(table.displayName()));
    }

    /** A question embedded with another model cannot be compared with the stored vectors. */
    private static void requireSameDimensions(EmbeddingResult question, DatasetSnapshot snapshot) {
        Integer expected = snapshot.dimensions();
        if (expected == null || expected != question.vector().length) {
            throw new ExplorerException(
                    ErrorCode.DIMENSION_MISMATCH,
                    "The question has %d dimensions but the table has %s: embed it with the model used to index the table."
                            .formatted(question.vector().length, expected == null ? "no vectors" : expected),
                    Map.of("expected", expected == null ? 0 : expected, "actual", question.vector().length));
        }
    }
}
