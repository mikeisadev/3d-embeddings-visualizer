package com.example.embeddingexplorer.embedding;

/** Used when {@code VIZ_EMBEDDING_ENABLED=false}: every call answers {@code EMBEDDING_DISABLED}. */
public final class DisabledEmbeddingClient implements EmbeddingClient {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public EmbeddingResult embed(String text) {
        throw EmbeddingClient.disabledError();
    }
}
