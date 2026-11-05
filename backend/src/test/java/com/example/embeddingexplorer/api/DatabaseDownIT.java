package com.example.embeddingexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The application starts and stays usable when no database is listening. */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseDownIT {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        registry.add("viz.db.url", () -> "jdbc:postgresql://127.0.0.1:" + closedPort + "/chatbot");
        registry.add("viz.db.password", () -> "irrelevant");
        registry.add("viz.db.connection-timeout", () -> "2s");
    }

    @Test
    void healthStaysUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void diagnosticsFailFastWithDbUnavailable() throws Exception {
        long start = System.nanoTime();

        mvc.perform(get("/api/v1/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.checks[0].id").value("db.connection"))
                .andExpect(jsonPath("$.checks[0].code").value("DB_UNAVAILABLE"))
                .andExpect(jsonPath("$.checks[0].facts.url").isString())
                .andExpect(jsonPath("$.checks[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.checks[9].id").value("embedding.client"));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void dataEndpointsAnswerWithDbUnavailable() throws Exception {
        mvc.perform(get("/api/v1/dataset"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
