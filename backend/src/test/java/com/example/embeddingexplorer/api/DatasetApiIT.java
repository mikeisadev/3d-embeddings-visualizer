package com.example.embeddingexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestVectors;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DatasetApiIT {

    private static final String TABLE = "api_dataset";
    private static final int DIMENSIONS = 32;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PgvectorDatabase.register(registry, TABLE);
    }

    @BeforeAll
    static void createTable() {
        PgvectorDatabase.createVectorTable(TABLE, DIMENSIONS);
        List<PgvectorDatabase.TestRow> rows = new ArrayList<>(SampleData.rows(DIMENSIONS, 4));
        rows.add(new PgvectorDatabase.TestRow(SampleData.uuid(9001), null, "[1, 2]", TestVectors.random(DIMENSIONS, 1)));
        rows.add(new PgvectorDatabase.TestRow(SampleData.uuid(9002), "senza vettore", "{}", null));
        PgvectorDatabase.insert(TABLE, rows);
        PgvectorDatabase.createHnswIndex(TABLE, "vector_cosine_ops");
    }

    @Test
    void summarizesTheTable() throws Exception {
        mvc.perform(get("/api/v1/dataset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("public"))
                .andExpect(jsonPath("$.table").value(TABLE))
                .andExpect(jsonPath("$.rowCount").value(14))
                .andExpect(jsonPath("$.rowsWithEmbedding").value(13))
                .andExpect(jsonPath("$.loadedRows").value(13))
                .andExpect(jsonPath("$.sampled").value(false))
                .andExpect(jsonPath("$.dimensions").value(DIMENSIONS))
                .andExpect(jsonPath("$.distanceMetric.metric").value("COSINE"))
                .andExpect(jsonPath("$.distanceMetric.source").value("DETECTED"))
                .andExpect(jsonPath("$.index.type").value("hnsw"))
                .andExpect(jsonPath("$.index.operatorClass").value("vector_cosine_ops"))
                .andExpect(jsonPath("$.files", hasSize(4)))
                .andExpect(jsonPath("$.files[0].chunks").value(4))
                .andExpect(jsonPath("$.files[3].file").value("Sconosciuto"))
                .andExpect(jsonPath("$.metadataKeys[?(@.key == 'page_number')].valueType").value("NUMBER"))
                .andExpect(jsonPath("$.metadataKeys[?(@.key == 'department')].distinctValues").value(2))
                .andExpect(jsonPath("$.warnings[?(@.code == 'NULL_EMBEDDINGS_SKIPPED')].count").value(1))
                .andExpect(jsonPath("$.warnings[?(@.code == 'METADATA_NOT_OBJECT')].count").value(1))
                .andExpect(jsonPath("$.warnings[?(@.code == 'CONTENT_NULL')].count").value(1))
                .andExpect(jsonPath("$.datasetVersion").isNotEmpty());
    }

    @Test
    void reportsDiagnosticsWithHttp200() throws Exception {
        mvc.perform(get("/api/v1/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARN"))
                .andExpect(jsonPath("$.checks", hasSize(10)))
                .andExpect(jsonPath("$.checks[0].id").value("db.connection"))
                .andExpect(jsonPath("$.checks[0].status").value("OK"))
                .andExpect(jsonPath("$.checkedAt").isString());
    }

    @Test
    void exposesOnlyTheHealthEndpoint() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
    }

    @Test
    void theApplicationConnectionIsReadOnly() {
        String readOnly = jdbc.sql("SELECT current_setting('transaction_read_only')").query(String.class).single();
        String timeout = jdbc.sql("SELECT current_setting('statement_timeout')").query(String.class).single();

        assertThat(readOnly).isEqualTo("on");
        assertThat(timeout).isEqualTo("15s");
    }
}
