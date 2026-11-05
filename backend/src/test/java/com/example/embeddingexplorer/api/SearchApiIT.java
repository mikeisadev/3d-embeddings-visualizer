package com.example.embeddingexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.FakeEmbeddingServer;
import com.example.embeddingexplorer.support.FakeEmbeddingServer.Response;
import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestVectors;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** "Simula una ricerca RAG" end to end, with a fake OpenAI-compatible provider. */
@SpringBootTest
@AutoConfigureMockMvc
class SearchApiIT {

    private static final String TABLE = "api_search";
    private static final int DIMENSIONS = 48;
    private static final List<PgvectorDatabase.TestRow> ROWS = SampleData.rows(DIMENSIONS, 8);
    private static final PgvectorDatabase.TestRow QUESTION_TWIN = ROWS.get(3);
    private static final FakeEmbeddingServer PROVIDER = new FakeEmbeddingServer();

    @Autowired
    private MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        PgvectorDatabase.register(registry, TABLE);
        registry.add("viz.embedding.enabled", () -> "true");
        registry.add("viz.embedding.base-url", PROVIDER::baseUrl);
        registry.add("viz.embedding.api-key", () -> "sk-test-key");
        registry.add("viz.search-rate-limit-per-minute", () -> "1000");
    }

    @BeforeAll
    static void createTable() {
        PgvectorDatabase.createVectorTable(TABLE, DIMENSIONS);
        PgvectorDatabase.insert(TABLE, ROWS);
        PgvectorDatabase.createHnswIndex(TABLE, "vector_cosine_ops");
        PROVIDER.respondWith(body -> {
            if (body.contains("dimensioni sbagliate")) {
                return Response.ok(TestVectors.random(DIMENSIONS + 1, 1));
            }
            if (body.contains("chiave sbagliata")) {
                return Response.status(401);
            }
            return Response.ok(QUESTION_TWIN.vector());
        });
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Test
    void returnsTheTopKWithTheThresholdOutcomeAndTheSpringAiSql() throws Exception {
        JsonNode result = search("{\"question\": \"Come chiedo le ferie?\", \"topK\": 12, \"similarityThreshold\": 0.5}");

        JsonNode results = result.get("results");
        assertThat(results).hasSize(12);
        JsonNode first = results.get(0);
        assertThat(first.get("id").asString()).isEqualTo(QUESTION_TWIN.id());
        assertThat(first.get("similarity").asDouble()).isCloseTo(1.0, within(1e-5));
        assertThat(first.get("passesThreshold").asBoolean()).isTrue();
        for (JsonNode match : results) {
            boolean expected = match.get("distance").asDouble() < 0.5;
            assertThat(match.get("passesThreshold").asBoolean()).isEqualTo(expected);
        }
        assertThat(results.valueStream().filter(match -> !match.get("passesThreshold").asBoolean())).isNotEmpty();
        assertThat(result.get("distanceThreshold").asDouble()).isCloseTo(0.5, within(1e-12));
        assertThat(result.get("model").asString()).isEqualTo("fake-embedding-model");
        assertThat(result.get("dimensions").asInt()).isEqualTo(DIMENSIONS);
        assertThat(result.get("springAiSql").asString()).isEqualTo(
                "SELECT *, embedding <=> ? AS distance FROM public.api_search WHERE embedding <=> ? < ? "
                        + "ORDER BY distance LIMIT ?");
        assertThat(PROVIDER.requests().getLast().authorization()).isEqualTo("Bearer sk-test-key");
    }

    @Test
    void appliesTheDefaultsOfTheL18RagService() throws Exception {
        JsonNode result = search("{\"question\": \"Domanda\"}");

        assertThat(result.get("topK").asInt()).isEqualTo(5);
        assertThat(result.get("similarityThreshold").asDouble()).isEqualTo(0.5);
        assertThat(result.get("results")).hasSize(5);
    }

    @Test
    void projectsTheQuestionWithTheSameTransformAsThePoints() throws Exception {
        JsonNode result = search("{\"question\": \"Domanda\", \"normalize\": true}");
        JsonNode points = json.readTree(mvc.perform(get("/api/v1/points?normalize=true"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode twin = points.get("points").valueStream()
                .filter(point -> point.get("id").asString().equals(QUESTION_TWIN.id()))
                .findFirst()
                .orElseThrow();
        JsonNode question = result.get("queryPoint");
        for (String axis : List.of("x", "y", "z")) {
            assertThat(question.get(axis).asDouble()).isCloseTo(twin.get(axis).asDouble(), within(1e-4));
        }
        assertThat(result.get("datasetVersion").asString()).isEqualTo(points.get("datasetVersion").asString());
    }

    @Test
    void refusesAQuestionEmbeddedWithAnotherModel() throws Exception {
        mvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"dimensioni sbagliate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIMENSION_MISMATCH"))
                .andExpect(jsonPath("$.expected").value(DIMENSIONS))
                .andExpect(jsonPath("$.actual").value(DIMENSIONS + 1));
    }

    @Test
    void reportsProviderErrorsWithoutLeakingTheKey() throws Exception {
        String body = mvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"chiave sbagliata\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMBEDDING_AUTH_FAILED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("sk-");
    }

    @Test
    void validatesTheRequest() throws Exception {
        for (String invalid : List.of(
                "{\"question\": \"   \"}",
                "{\"question\": \"" + "x".repeat(2001) + "\"}",
                "{\"question\": \"ok\", \"topK\": 21}",
                "{\"question\": \"ok\", \"topK\": 0}",
                "{\"question\": \"ok\", \"similarityThreshold\": 1.5}")) {
            mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void runsTheLiveTestOfTheProvider() throws Exception {
        mvc.perform(post("/api/v1/diagnostics/embedding-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("fake-embedding-model"))
                .andExpect(jsonPath("$.dimensions").value(DIMENSIONS));
        mvc.perform(get("/api/v1/diagnostics"))
                .andExpect(jsonPath("$.checks[9].id").value("embedding.client"))
                .andExpect(jsonPath("$.checks[9].status").value("OK"))
                .andExpect(jsonPath("$.checks[9].facts.apiKeyPresent").value(true));
    }

    private JsonNode search(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }
}
