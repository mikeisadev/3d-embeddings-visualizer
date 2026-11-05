package com.example.embeddingexplorer.embedding;

import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls any OpenAI-compatible {@code POST /embeddings} endpoint: OpenAI itself, Ollama
 * ({@code http://host.docker.internal:11434/v1}) or Docker Model Runner
 * ({@code http://model-runner.docker.internal/engines/v1}).
 *
 * <p>Failures become stable error codes. Messages never contain the API key nor the body of
 * the provider's error (OpenAI echoes part of the key there), and the question text is never
 * logged.
 */
public final class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final String model;

    public OpenAiCompatibleEmbeddingClient(ExplorerProperties.Embedding config) {
        this(config, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    OpenAiCompatibleEmbeddingClient(ExplorerProperties.Embedding config, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(withoutTrailingSlash(config.baseUrl()))
                .requestFactory(requestFactory);
        if (config.hasApiKey()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey().strip());
        }
        this.restClient = builder.build();
        this.model = config.model();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public EmbeddingResult embed(String text) {
        long start = System.nanoTime();
        EmbeddingsResponse response;
        try {
            response = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingsRequest(model, text, "float"))
                    .retrieve()
                    .body(EmbeddingsResponse.class);
        } catch (RestClientResponseException failure) {
            throw fromStatus(failure.getStatusCode().value(), failure);
        } catch (ResourceAccessException failure) {
            throw isTimeout(failure)
                    ? new ExplorerException(
                            ErrorCode.EMBEDDING_TIMEOUT, "The embedding provider did not answer in time.", failure)
                    : new ExplorerException(
                            ErrorCode.EMBEDDING_PROVIDER_ERROR,
                            "The embedding provider is not reachable at the configured base URL.",
                            failure);
        } catch (RestClientException failure) {
            throw new ExplorerException(
                    ErrorCode.EMBEDDING_PROVIDER_ERROR, "The embedding provider sent an unreadable response.", failure);
        }
        long latencyMillis = (System.nanoTime() - start) / 1_000_000;
        return new EmbeddingResult(vectorOf(response), modelOf(response), latencyMillis);
    }

    private float[] vectorOf(EmbeddingsResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw invalidResponse("no embedding in the response");
        }
        float[] vector = response.data().getFirst().embedding();
        if (vector == null || vector.length == 0) {
            throw invalidResponse("an empty embedding");
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw invalidResponse("an embedding with invalid numbers");
            }
        }
        return vector;
    }

    private String modelOf(EmbeddingsResponse response) {
        return response.model() == null || response.model().isBlank() ? model : response.model();
    }

    private static ExplorerException fromStatus(int status, Exception cause) {
        ErrorCode code = switch (status) {
            case 401, 403 -> ErrorCode.EMBEDDING_AUTH_FAILED;
            case 404 -> ErrorCode.EMBEDDING_MODEL_NOT_FOUND;
            case 429 -> ErrorCode.EMBEDDING_RATE_LIMITED;
            default -> ErrorCode.EMBEDDING_PROVIDER_ERROR;
        };
        return new ExplorerException(
                code, "The embedding provider answered HTTP " + status + ".", Map.of("providerStatus", status), cause);
    }

    private static ExplorerException invalidResponse(String what) {
        return new ExplorerException(ErrorCode.EMBEDDING_PROVIDER_ERROR, "The embedding provider sent " + what + ".");
    }

    private static boolean isTimeout(Throwable failure) {
        return Stream.iterate(failure, cause -> cause != null, Throwable::getCause)
                .limit(16)
                .anyMatch(HttpTimeoutException.class::isInstance);
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Request body of the OpenAI embeddings API. */
    record EmbeddingsRequest(String model, String input, @JsonProperty("encoding_format") String encodingFormat) {}

    /** The part of the OpenAI embeddings response the tool needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingsResponse(List<Item> data, String model) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Item(float[] embedding) {}
    }
}
