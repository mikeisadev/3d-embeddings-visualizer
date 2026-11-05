package com.example.embeddingexplorer.api;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestVectors;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The database stops while the application runs and then comes back: the application reports
 * {@code DB_UNAVAILABLE} meanwhile and recovers by itself, without a restart. The container
 * uses a fixed host port, so that it keeps its address across {@code docker stop/start}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseRecoveryIT {

    private static final int HOST_PORT = freePort();
    private static final PostgreSQLContainer DATABASE = startDatabase();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("viz.db.url", () -> "jdbc:postgresql://127.0.0.1:" + HOST_PORT + "/test");
        registry.add("viz.db.user", DATABASE::getUsername);
        registry.add("viz.db.password", DATABASE::getPassword);
        registry.add("viz.db.connection-timeout", () -> "2s");
    }

    @AfterAll
    static void stop() {
        DATABASE.stop();
    }

    @Test
    void recoversWhenTheDatabaseComesBack() throws Exception {
        mvc.perform(get("/api/v1/dataset")).andExpect(status().isOk()).andExpect(jsonPath("$.rowCount").value(3));

        DATABASE.getDockerClient().stopContainerCmd(DATABASE.getContainerId()).exec();

        mvc.perform(get("/api/v1/dataset"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"));
        mvc.perform(get("/api/v1/diagnostics")).andExpect(jsonPath("$.checks[0].code").value("DB_UNAVAILABLE"));

        DATABASE.getDockerClient().startContainerCmd(DATABASE.getContainerId()).exec();

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> mvc.perform(
                        get("/api/v1/dataset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(3)));
        mvc.perform(get("/api/v1/diagnostics")).andExpect(jsonPath("$.checks[0].status").value("OK"));
    }

    private static PostgreSQLContainer startDatabase() {
        PostgreSQLContainer container = new PostgreSQLContainer(PgvectorDatabase.IMAGE)
                .withCreateContainerCmdModifier(command -> Objects.requireNonNull(command.getHostConfig())
                        .withPortBindings(new PortBinding(Ports.Binding.bindPort(HOST_PORT), new ExposedPort(5432))));
        container.start();
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword()));
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector");
        admin.execute("CREATE TABLE public.vector_store (id uuid PRIMARY KEY, content text, metadata json, embedding vector(4))");
        for (int i = 0; i < 3; i++) {
            admin.update(
                    "INSERT INTO public.vector_store VALUES (?::uuid, ?, '{}'::json, ?::vector)",
                    SampleData.uuid(i),
                    "chunk " + i,
                    TestVectors.literal(TestVectors.random(4, i)));
        }
        return container;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port", e);
        }
    }
}
