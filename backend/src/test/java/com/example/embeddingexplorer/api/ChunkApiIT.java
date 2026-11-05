package com.example.embeddingexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestVectors;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Points, chunk details, real neighbors, comparisons and text search through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
class ChunkApiIT {

    private static final String TABLE = "api_chunks";
    private static final int DIMENSIONS = 64;
    private static final int PER_FILE = 20;
    private static final List<PgvectorDatabase.TestRow> ROWS = SampleData.rows(DIMENSIONS, PER_FILE);
    private static final Map<String, float[]> VECTORS = ROWS.stream()
            .collect(Collectors.toMap(PgvectorDatabase.TestRow::id, PgvectorDatabase.TestRow::vector));

    @Autowired
    private MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PgvectorDatabase.register(registry, TABLE);
    }

    @BeforeAll
    static void createTable() {
        PgvectorDatabase.createVectorTable(TABLE, DIMENSIONS);
        PgvectorDatabase.insert(TABLE, ROWS);
        PgvectorDatabase.createHnswIndex(TABLE, "vector_cosine_ops");
    }

    @Test
    void pointsCarryTheProjectionAndOneEntryPerChunk() throws Exception {
        JsonNode body = getJson("/api/v1/points");

        assertThat(body.get("datasetVersion").asString()).startsWith(ROWS.size() + "-");
        JsonNode projection = body.get("projection");
        assertThat(projection.get("method").asString()).isEqualTo("pca");
        assertThat(projection.get("normalized").asBoolean()).isTrue();
        assertThat(projection.get("dimensions").asInt()).isEqualTo(DIMENSIONS);
        assertThat(projection.get("explainedVarianceRatio")).hasSize(3);
        assertThat(projection.get("totalExplainedVariance").asDouble()).isBetween(0.0, 1.0);
        JsonNode points = body.get("points");
        assertThat(points).hasSize(ROWS.size());
        JsonNode first = points.get(0);
        assertThat(first.get("file").asString()).isEqualTo("ferie.pdf");
        assertThat(first.get("page").asInt()).isEqualTo(1);
        assertThat(first.get("preview").asString()).startsWith("Chunk 0 of ferie.pdf");
        assertThat(first.get("metadata").get("department").asString()).isEqualTo("HR");
        assertThat(first.has("x") && first.has("y") && first.has("z")).isTrue();
    }

    @Test
    void theNormalizeFlagIsReported() throws Exception {
        assertThat(getJson("/api/v1/points?normalize=false").get("projection").get("normalized").asBoolean())
                .isFalse();
    }

    @Test
    void chunkDetailsContainTheFullVectorAndItsStats() throws Exception {
        PgvectorDatabase.TestRow row = ROWS.get(5);

        JsonNode chunk = getJson("/api/v1/chunks/" + row.id());

        assertThat(chunk.get("content").asString()).isEqualTo(row.content());
        assertThat(chunk.get("metadata").get("original_filename").asString()).isEqualTo("ferie.pdf");
        assertThat(chunk.get("embedding")).hasSize(DIMENSIONS);
        assertThat(chunk.get("embedding").get(3).floatValue()).isEqualTo(row.vector()[3]);
        assertThat(chunk.get("stats").get("dimensions").asInt()).isEqualTo(DIMENSIONS);
        assertThat(chunk.get("stats").get("norm").asDouble()).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void unknownChunksAreNotFound() throws Exception {
        mvc.perform(get("/api/v1/chunks/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHUNK_NOT_FOUND"));
    }

    @Test
    void neighborsAreTheRealClosestChunksExcludingItself() throws Exception {
        String id = ROWS.get(0).id();

        JsonNode response = getJson("/api/v1/chunks/" + id + "/neighbors?k=5");

        List<String> expected = VECTORS.keySet().stream()
                .filter(other -> !other.equals(id))
                .sorted(Comparator.comparingDouble(other ->
                        -TestVectors.cosineSimilarity(VECTORS.get(id), VECTORS.get(other))))
                .limit(5)
                .toList();
        JsonNode neighbors = response.get("neighbors");
        assertThat(neighbors).hasSize(5);
        assertThat(neighbors.valueStream().map(neighbor -> neighbor.get("id").asString()).toList())
                .containsExactlyElementsOf(expected);
        JsonNode closest = neighbors.get(0);
        assertThat(closest.get("rank").asInt()).isEqualTo(1);
        assertThat(closest.get("similarity").asDouble())
                .isCloseTo(1 - closest.get("distance").asDouble(), within(1e-12))
                .isCloseTo(TestVectors.cosineSimilarity(VECTORS.get(id), VECTORS.get(expected.getFirst())), within(1e-5));
        assertThat(response.get("exact").asBoolean()).isTrue();
        assertThat(response.get("metric").get("metric").asString()).isEqualTo("COSINE");
        assertThat(response.get("sql").asString()).contains("ORDER BY embedding <=> CAST(? AS vector)");
    }

    @Test
    void neighborsGoBeyondTheHnswCandidateList() throws Exception {
        JsonNode neighbors = getJson("/api/v1/chunks/" + ROWS.get(0).id() + "/neighbors?k=50").get("neighbors");

        assertThat(neighbors).hasSize(50);
    }

    @Test
    void kIsValidated() throws Exception {
        String id = ROWS.get(0).id();
        mvc.perform(get("/api/v1/chunks/" + id + "/neighbors?k=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/chunks/" + id + "/neighbors?k=0")).andExpect(status().isBadRequest());
    }

    @Test
    void compareComputesEveryMetricOnTheFullVectors() throws Exception {
        PgvectorDatabase.TestRow a = ROWS.get(1);
        PgvectorDatabase.TestRow b = ROWS.get(PER_FILE + 1);

        JsonNode comparison = getJson("/api/v1/compare?a=" + a.id() + "&b=" + b.id());

        double cosine = TestVectors.cosineSimilarity(a.vector(), b.vector());
        assertThat(comparison.get("cosineSimilarity").asDouble()).isCloseTo(cosine, within(1e-5));
        assertThat(comparison.get("cosineDistance").asDouble()).isCloseTo(1 - cosine, within(1e-5));
        assertThat(comparison.get("dotProduct").asDouble()).isCloseTo(cosine, within(1e-5));
        assertThat(comparison.get("euclideanDistance").asDouble()).isCloseTo(Math.sqrt(2 - 2 * cosine), within(1e-4));
    }

    @Test
    void compareReportsTheMissingChunk() throws Exception {
        mvc.perform(get("/api/v1/compare").param("a", ROWS.get(0).id()).param("b", "missing-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHUNK_NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString("missing-id")));
    }

    @Test
    void textSearchLooksAtTheFullContentIgnoringCase() throws Exception {
        mvc.perform(get("/api/v1/chunks").param("contains", "WELFARE.PDF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(PER_FILE)))
                .andExpect(jsonPath("$.truncated").value(false));
        mvc.perform(get("/api/v1/chunks").param("contains", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void searchTermsAreBoundNotInterpreted() throws Exception {
        mvc.perform(get("/api/v1/chunks").param("contains", "%' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(0)));
    }

    private JsonNode getJson(String path) throws Exception {
        String body = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body);
    }
}
