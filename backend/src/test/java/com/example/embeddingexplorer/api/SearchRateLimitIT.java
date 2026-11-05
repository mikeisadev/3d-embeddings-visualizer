package com.example.embeddingexplorer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.FakeEmbeddingServer;
import com.example.embeddingexplorer.support.FakeEmbeddingServer.Response;
import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
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

/** The paid provider is protected: searches and live tests share a small per-minute budget. */
@SpringBootTest
@AutoConfigureMockMvc
class SearchRateLimitIT {

    private static final String TABLE = "api_search_limit";
    private static final int DIMENSIONS = 16;
    private static final FakeEmbeddingServer PROVIDER = new FakeEmbeddingServer();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        PgvectorDatabase.register(registry, TABLE);
        registry.add("viz.embedding.enabled", () -> "true");
        registry.add("viz.embedding.base-url", PROVIDER::baseUrl);
        registry.add("viz.search-rate-limit-per-minute", () -> "2");
    }

    @BeforeAll
    static void createTable() {
        PgvectorDatabase.createVectorTable(TABLE, DIMENSIONS);
        PgvectorDatabase.insert(TABLE, SampleData.rows(DIMENSIONS, 2));
        PROVIDER.respondWith(body -> Response.ok(SampleData.rows(DIMENSIONS, 1).getFirst().vector()));
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Test
    void theThirdCallInAMinuteIsRateLimited() throws Exception {
        String question = "{\"question\": \"ferie\"}";
        mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/diagnostics/embedding-test")).andExpect(status().isOk());

        mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(header().exists("Retry-After"));
    }
}
