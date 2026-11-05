package com.example.embeddingexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The raw vectors for the UMAP worker and the neighbor sample for the projection fidelity score. */
@SpringBootTest
@AutoConfigureMockMvc
class VectorsApiIT {

    private static final String TABLE = "api_vectors";
    private static final int DIMENSIONS = 48;
    private static final List<PgvectorDatabase.TestRow> ROWS = SampleData.rows(DIMENSIONS, 15);
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
    void streamsTheVectorsInTheOrderOfThePoints() throws Exception {
        JsonNode points = getJson("/api/v1/points");
        String version = points.get("datasetVersion").asString();

        MvcResult started = mvc.perform(get("/api/v1/vectors").param("datasetVersion", version))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Rows", String.valueOf(ROWS.size())))
                .andExpect(header().string("X-Dimensions", String.valueOf(DIMENSIONS)))
                .andExpect(header().string("X-Dataset-Version", version))
                .andExpect(header().longValue("Content-Length", (long) ROWS.size() * DIMENSIONS * Float.BYTES))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).hasSize(ROWS.size() * DIMENSIONS * Float.BYTES);
        FloatBuffer values = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        for (int row = 0; row < ROWS.size(); row++) {
            String id = points.get("points").get(row).get("id").asString();
            float[] actual = new float[DIMENSIONS];
            values.get(row * DIMENSIONS, actual);
            assertThat(actual).as("row %d (%s)", row, id).containsExactly(VECTORS.get(id));
        }
    }

    @Test
    void refusesAVersionThatIsNotTheCurrentOne() throws Exception {
        String current = getJson("/api/v1/dataset").get("datasetVersion").asString();

        mvc.perform(get("/api/v1/vectors").param("datasetVersion", "1-0000000000000000"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DATASET_CHANGED"))
                .andExpect(jsonPath("$.currentVersion").value(current));
    }

    @Test
    void requiresTheVersion() throws Exception {
        mvc.perform(get("/api/v1/vectors"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void theNeighborSampleMatchesTheExactDatabaseSearch() throws Exception {
        JsonNode sample = getJson("/api/v1/neighbors/sample?size=12&k=5");

        assertThat(sample.get("datasetVersion").asString()).startsWith(ROWS.size() + "-");
        assertThat(sample.get("metric").get("metric").asString()).isEqualTo("COSINE");
        assertThat(sample.get("k").asInt()).isEqualTo(5);
        assertThat(sample.get("loadedChunks").asInt()).isEqualTo(ROWS.size());
        assertThat(sample.get("samples")).hasSize(12);
        for (JsonNode entry : sample.get("samples")) {
            String id = entry.get("id").asString();
            JsonNode database = getJson("/api/v1/chunks/" + id + "/neighbors?k=5");
            assertThat(ids(entry.get("neighbors"))).as(id).containsExactlyElementsOf(ids(database.get("neighbors")));
        }
    }

    @Test
    void samplesEveryChunkOfASmallTableByDefault() throws Exception {
        JsonNode sample = getJson("/api/v1/neighbors/sample");

        assertThat(sample.get("samples")).hasSize(ROWS.size());
        assertThat(sample.get("k").asInt()).isEqualTo(10);
        assertThat(sample.get("samples").get(0).get("neighbors")).hasSize(10);
    }

    @Test
    void validatesTheSampleParameters() throws Exception {
        mvc.perform(get("/api/v1/neighbors/sample").param("size", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/neighbors/sample").param("k", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** Ids of a JSON array of strings, or of objects with an {@code id}. */
    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : array) {
            ids.add(item.isString() ? item.asString() : item.get("id").asString());
        }
        return ids;
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body);
    }
}
