package com.example.embeddingexplorer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A plain PostgreSQL without pgvector: the classic "type vector does not exist" mistake. */
@SpringBootTest
@AutoConfigureMockMvc
class PgvectorMissingIT {

    private static final PostgreSQLContainer PLAIN_POSTGRES = startPlainPostgres();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("viz.db.url", PLAIN_POSTGRES::getJdbcUrl);
        registry.add("viz.db.user", PLAIN_POSTGRES::getUsername);
        registry.add("viz.db.password", PLAIN_POSTGRES::getPassword);
    }

    @AfterAll
    static void stop() {
        PLAIN_POSTGRES.stop();
    }

    @Test
    void diagnosticsReportTheMissingExtension() throws Exception {
        mvc.perform(get("/api/v1/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.checks[0].status").value("OK"))
                .andExpect(jsonPath("$.checks[1].id").value("db.pgvector"))
                .andExpect(jsonPath("$.checks[1].status").value("FAIL"))
                .andExpect(jsonPath("$.checks[1].code").value("PGVECTOR_MISSING"))
                .andExpect(jsonPath("$.checks[2].status").value("SKIPPED"));
    }

    @Test
    void datasetEndpointsAnswerWithPgvectorMissing() throws Exception {
        mvc.perform(get("/api/v1/dataset"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PGVECTOR_MISSING"));
    }

    private static PostgreSQLContainer startPlainPostgres() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }
}
