package com.example.embeddingexplorer.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.support.FakeEmbeddingServer;
import com.example.embeddingexplorer.support.FakeEmbeddingServer.Response;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenAiCompatibleEmbeddingClientTest {

    private final FakeEmbeddingServer server = new FakeEmbeddingServer();

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void sendsTheOpenAiRequestAndReadsTheVector() {
        server.respondWith(body -> Response.ok(new float[] {0.6f, -0.8f}));

        EmbeddingResult result = client("sk-test").embed("Quanti giorni di ferie?");

        assertThat(result.vector()).containsExactly(0.6f, -0.8f);
        assertThat(result.model()).isEqualTo("fake-embedding-model");
        FakeEmbeddingServer.Request request = server.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.authorization()).isEqualTo("Bearer sk-test");
        assertThat(request.body())
                .contains("\"model\":\"text-embedding-3-small\"")
                .contains("\"input\":\"Quanti giorni di ferie?\"")
                .contains("\"encoding_format\":\"float\"");
    }

    @Test
    void sendsNoAuthorizationWithoutAKey() {
        client(" ").embed("ciao");

        assertThat(server.requests().getFirst().authorization()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "401, EMBEDDING_AUTH_FAILED",
        "403, EMBEDDING_AUTH_FAILED",
        "404, EMBEDDING_MODEL_NOT_FOUND",
        "429, EMBEDDING_RATE_LIMITED",
        "400, EMBEDDING_PROVIDER_ERROR",
        "500, EMBEDDING_PROVIDER_ERROR",
        "503, EMBEDDING_PROVIDER_ERROR"
    })
    void mapsProviderStatusesToErrorCodes(int status, ErrorCode expected) {
        server.respondWith(body -> Response.status(status));

        assertThatThrownBy(() -> client("sk-secret-key").embed("domanda"))
                .isInstanceOfSatisfying(ExplorerException.class, error -> {
                    assertThat(error.code()).isEqualTo(expected);
                    assertThat(error.getMessage()).contains(String.valueOf(status)).doesNotContain("sk-");
                    assertThat(error.properties()).containsEntry("providerStatus", status);
                });
    }

    @Test
    void reportsATimeout() {
        server.respondWith(body -> Response.slow(Duration.ofSeconds(3)));
        OpenAiCompatibleEmbeddingClient impatient = new OpenAiCompatibleEmbeddingClient(
                config(server.baseUrl(), ""), Duration.ofSeconds(1), Duration.ofMillis(300));

        assertThatThrownBy(() -> impatient.embed("domanda"))
                .isInstanceOfSatisfying(ExplorerException.class, error -> assertThat(error.code())
                        .isEqualTo(ErrorCode.EMBEDDING_TIMEOUT));
    }

    @Test
    void reportsAnUnreachableProvider() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        OpenAiCompatibleEmbeddingClient unreachable =
                new OpenAiCompatibleEmbeddingClient(config("http://127.0.0.1:" + closedPort + "/v1", ""));

        assertThatThrownBy(() -> unreachable.embed("domanda"))
                .isInstanceOfSatisfying(ExplorerException.class, error -> assertThat(error.code())
                        .isEqualTo(ErrorCode.EMBEDDING_PROVIDER_ERROR));
    }

    @Test
    void rejectsResponsesWithoutAUsableVector() {
        for (String body : new String[] {"{\"data\": []}", "{\"data\": [{\"embedding\": []}]}", "not json"}) {
            server.respondWith(request -> Response.body(body));

            assertThatThrownBy(() -> client("").embed("domanda"))
                    .as(body)
                    .isInstanceOfSatisfying(ExplorerException.class, error -> assertThat(error.code())
                            .isEqualTo(ErrorCode.EMBEDDING_PROVIDER_ERROR));
        }
    }

    @Test
    void theDisabledClientAlwaysAnswersEmbeddingDisabled() {
        DisabledEmbeddingClient disabled = new DisabledEmbeddingClient();

        assertThat(disabled.enabled()).isFalse();
        assertThatThrownBy(disabled::requireEnabled)
                .isInstanceOfSatisfying(ExplorerException.class, error -> assertThat(error.code())
                        .isEqualTo(ErrorCode.EMBEDDING_DISABLED));
        assertThatThrownBy(() -> disabled.embed("x")).isInstanceOf(ExplorerException.class);
    }

    private OpenAiCompatibleEmbeddingClient client(String apiKey) {
        return new OpenAiCompatibleEmbeddingClient(config(server.baseUrl() + "/", apiKey));
    }

    private static ExplorerProperties.Embedding config(String baseUrl, String apiKey) {
        return new ExplorerProperties.Embedding(true, baseUrl, "text-embedding-3-small", apiKey);
    }
}
