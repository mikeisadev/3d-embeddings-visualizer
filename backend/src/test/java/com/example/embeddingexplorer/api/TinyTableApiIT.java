package com.example.embeddingexplorer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestVectors;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Two chunks are not enough for a 3D projection, but the summary still works. */
@SpringBootTest
@AutoConfigureMockMvc
class TinyTableApiIT {

    private static final String TABLE = "api_tiny";

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PgvectorDatabase.register(registry, TABLE);
    }

    @BeforeAll
    static void createTable() {
        PgvectorDatabase.createVectorTable(TABLE, 8);
        PgvectorDatabase.insert(TABLE, List.of(
                new PgvectorDatabase.TestRow(SampleData.uuid(1), "uno", "{}", TestVectors.random(8, 1)),
                new PgvectorDatabase.TestRow(SampleData.uuid(2), "due", "{}", TestVectors.random(8, 2))));
    }

    @Test
    void pointsAreNotPossibleWithFewerThanThreeVectors() throws Exception {
        mvc.perform(get("/api/v1/points"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROJECTION_NOT_POSSIBLE"));
    }

    @Test
    void theSummaryStillAnswers() throws Exception {
        mvc.perform(get("/api/v1/dataset")).andExpect(status().isOk()).andExpect(jsonPath("$.loadedRows").value(2));
    }
}
