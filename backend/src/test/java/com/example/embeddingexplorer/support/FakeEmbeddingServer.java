package com.example.embeddingexplorer.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A real HTTP server playing an OpenAI-compatible {@code /v1/embeddings} endpoint, so the
 * client is tested with real sockets, status codes and timeouts.
 */
public final class FakeEmbeddingServer implements AutoCloseable {

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Function<String, Response> responder = body -> Response.ok(new float[] {1, 0, 0});

    public FakeEmbeddingServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the fake embedding server", e);
        }
        server.createContext("/v1/embeddings", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** Decides the response from the request body. */
    public void respondWith(Function<String, Response> responder) {
        this.responder = responder;
    }

    public List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(
                exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Authorization"), body));
        Response response = responder.apply(body);
        if (!response.delay().isZero()) {
            try {
                Thread.sleep(response.delay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * A received request.
     *
     * @param method        HTTP method
     * @param authorization Authorization header, or {@code null}
     * @param body          request body
     */
    public record Request(String method, String authorization, String body) {}

    /**
     * What the server answers.
     *
     * @param status HTTP status
     * @param body   response body
     * @param delay  wait before answering
     */
    public record Response(int status, String body, Duration delay) {

        public static Response ok(float[] vector) {
            return new Response(
                    200,
                    """
                    {"object": "list", "data": [{"object": "embedding", "index": 0, "embedding": %s}],
                     "model": "fake-embedding-model", "usage": {"prompt_tokens": 3, "total_tokens": 3}}"""
                            .formatted(TestVectors.literal(vector)),
                    Duration.ZERO);
        }

        public static Response status(int status) {
            return new Response(status, "{\"error\": {\"message\": \"Incorrect API key provided: sk-ab***yz\"}}", Duration.ZERO);
        }

        public static Response slow(Duration delay) {
            return new Response(200, "{}", delay);
        }

        public static Response body(String body) {
            return new Response(200, body, Duration.ZERO);
        }
    }
}
